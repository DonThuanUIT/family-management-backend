package com.familyfund.audit.aspect;

import com.familyfund.audit.annotation.Audited;
import com.familyfund.audit.model.AuditAction;
import com.familyfund.audit.model.AuditEntityType;
import com.familyfund.audit.snapshot.AuditSnapshotProvider;
import com.familyfund.audit.writer.AuditLogWriter;
import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.core.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.*;

/**
 * AOP Aspect intercepting methods annotated with {@link Audited}.
 * Captures pre- and post-mutation state snapshots to record a diff audit trail.
 */
@Slf4j
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class AuditAspect {

    private final Map<AuditEntityType, AuditSnapshotProvider> snapshotProviders = new EnumMap<>(AuditEntityType.class);
    private final AuditLogWriter auditLogWriter;
    private final CurrentUserProvider currentUserProvider;
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    @PersistenceContext
    private EntityManager entityManager;

    public AuditAspect(
            List<AuditSnapshotProvider> providers,
            AuditLogWriter auditLogWriter,
            CurrentUserProvider currentUserProvider
    ) {
        for (AuditSnapshotProvider provider : providers) {
            this.snapshotProviders.put(provider.supports(), provider);
        }
        this.auditLogWriter = auditLogWriter;
        this.currentUserProvider = currentUserProvider;
    }

    public void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Around("@annotation(audited)")
    public Object auditMethod(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        String clanId = TenantContext.getClanId();
        if (clanId == null || clanId.isBlank()) {
            clanId = "CLAN_DEFAULT";
        }

        AuditAction action = audited.action();
        AuditEntityType entityType = audited.entityType();
        AuditSnapshotProvider provider = snapshotProviders.get(entityType);

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();

        // 1. Resolve pre-execution entity ID if possible
        String entityId = resolveEntityId(audited.entityId(), method, args, null);

        // 2. Pre-execution snapshot
        Map<String, Object> oldValue = null;
        if (action != AuditAction.CREATE && provider != null && entityId != null && !entityId.isBlank()) {
            oldValue = provider.snapshot(entityId, clanId).orElse(null);
        }

        // 3. Execute business logic
        Object result = joinPoint.proceed();

        // Flush dirty entities in the current transaction so JDBC-based snapshot providers see latest data
        flushEntityManager();

        // 4. Post-execution entity ID resolution (especially for CREATE where ID comes from result)
        if (entityId == null || entityId.isBlank()) {
            entityId = resolveEntityId(audited.entityId(), method, args, result);
        }

        // 5. Post-execution snapshot
        Map<String, Object> newValue = null;
        if (action != AuditAction.DELETE && provider != null && entityId != null && !entityId.isBlank()) {
            newValue = provider.snapshot(entityId, clanId).orElse(null);
        }

        // If entityId was symbolic (e.g. "ROOT"), resolve actual entity ID from snapshots
        if ("ROOT".equalsIgnoreCase(entityId)) {
            if (newValue != null && newValue.get("branchId") != null) {
                entityId = String.valueOf(newValue.get("branchId"));
            } else if (oldValue != null && oldValue.get("branchId") != null) {
                entityId = String.valueOf(oldValue.get("branchId"));
            }
        }

        // 6. Avoid logging if no state change occurred (except CREATE / DELETE)
        if (action != AuditAction.CREATE && action != AuditAction.DELETE && Objects.equals(oldValue, newValue)) {
            log.debug("Skipping audit log for {} {} as state is identical", action, entityId);
            return result;
        }

        // 7. Extract Actor & IP
        String actorAccountId = currentUserProvider.currentAccountId().orElse("SYSTEM");
        String ipAddress = getClientIp();

        // 8. Determine target member ID if available
        String targetMemberId = null;
        if (entityType == AuditEntityType.MEMBER && entityId != null) {
            targetMemberId = entityId;
        } else if (newValue != null && newValue.containsKey("memberId")) {
            targetMemberId = String.valueOf(newValue.get("memberId"));
        } else if (oldValue != null && oldValue.containsKey("memberId")) {
            targetMemberId = String.valueOf(oldValue.get("memberId"));
        }

        // 9. Persist audit log
        auditLogWriter.writeLog(
                clanId,
                actorAccountId,
                action,
                entityType,
                entityId,
                targetMemberId,
                oldValue,
                newValue,
                ipAddress,
                audited.description()
        );

        return result;
    }

    private void flushEntityManager() {
        if (entityManager != null) {
            try {
                entityManager.flush();
            } catch (Exception ex) {
                log.debug("EntityManager flush before snapshot skipped: {}", ex.getMessage());
            }
        }
    }

    private String resolveEntityId(String spelExpression, Method method, Object[] args, Object result) {
        if (spelExpression != null && !spelExpression.isBlank()) {
            try {
                EvaluationContext context = new StandardEvaluationContext();
                String[] paramNames = parameterNameDiscoverer.getParameterNames(method);
                if (paramNames != null) {
                    for (int i = 0; i < paramNames.length; i++) {
                        context.setVariable(paramNames[i], args[i]);
                    }
                }
                if (result != null) {
                    context.setVariable("result", result);
                }
                Object val = expressionParser.parseExpression(spelExpression).getValue(context);
                return val != null ? String.valueOf(val).trim() : null;
            } catch (Exception ex) {
                log.warn("Failed to evaluate SpEL entityId expression '{}': {}", spelExpression, ex.getMessage());
            }
        }

        // Fallback: search common ID argument names
        String[] paramNames = parameterNameDiscoverer.getParameterNames(method);
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                String name = paramNames[i].toLowerCase(Locale.ROOT);
                if (name.equals("id") || name.equals("entityid") || name.equals("branchid") ||
                        name.equals("memberid") || name.equals("requestid") || name.equals("eventid")) {
                    if (args[i] != null) {
                        return String.valueOf(args[i]).trim();
                    }
                }
            }
        }

        return null;
    }

    private String getClientIp() {
        try {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
                HttpServletRequest request = attributes.getRequest();
                String xff = request.getHeader("X-Forwarded-For");
                if (xff != null && !xff.isBlank()) {
                    return xff.split(",")[0].trim();
                }
                return request.getRemoteAddr();
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
