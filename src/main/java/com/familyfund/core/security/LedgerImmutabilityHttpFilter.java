package com.familyfund.core.security;

import com.familyfund.core.exception.LedgerImmutabilityViolationException;
import com.familyfund.core.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * HTTP-layer immutability guard for all {@code /api/ledger/**} and {@code /api/v1/ledger/**} routes
 * (NFR-01 / US-61).
 *
 * <p>Unconditionally blocks {@code PUT}, {@code PATCH}, and {@code DELETE} (as well as
 * {@code X-HTTP-Method-Override} attempts) for every caller—including {@code ROLE_ADMIN} and
 * {@code ROLE_CLAN_HEAD}—returning {@code 405 Method Not Allowed} with
 * {@code Allow: GET, POST, HEAD, OPTIONS} and structured error code
 * {@link LedgerImmutabilityViolationException#ERROR_CODE}.</p>
 *
 * <p>Intentionally not annotated with {@code @Component} so Spring Boot only registers it inside
 * {@link com.familyfund.core.config.SecurityConfig} right after {@link JwtAuthenticationFilter}.</p>
 */
@Slf4j
public class LedgerImmutabilityHttpFilter extends OncePerRequestFilter {

    private static final String ALLOWED_METHODS_HEADER = "GET, POST, HEAD, OPTIONS";
    private static final Set<String> BLOCKED_METHODS = Set.of("PUT", "PATCH", "DELETE");
    private static final String METHOD_OVERRIDE_HEADER = "X-HTTP-Method-Override";
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = normalizePath(request);
        if (isLedgerPath(path)) {
            String effectiveMethod = resolveBlockedMethod(request);
            if (effectiveMethod != null) {
                logSecurityAlert(request, path, effectiveMethod);
                writeMethodNotAllowedResponse(response, effectiveMethod);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private static String normalizePath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null || uri.isBlank()) {
            return "";
        }
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }

    private static boolean isLedgerPath(String path) {
        return "/api/ledger".equals(path)
                || path.startsWith("/api/ledger/")
                || "/api/v1/ledger".equals(path)
                || path.startsWith("/api/v1/ledger/");
    }

    private static String resolveBlockedMethod(HttpServletRequest request) {
        String method = request.getMethod();
        if (method != null) {
            String upper = method.trim().toUpperCase(Locale.ROOT);
            if (BLOCKED_METHODS.contains(upper)) {
                return upper;
            }
        }
        String override = request.getHeader(METHOD_OVERRIDE_HEADER);
        if (override != null && !override.isBlank()) {
            String upperOverride = override.trim().toUpperCase(Locale.ROOT);
            if (BLOCKED_METHODS.contains(upperOverride)) {
                return upperOverride;
            }
        }
        return null;
    }

    private static void logSecurityAlert(HttpServletRequest request, String path, String method) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String actor = (auth != null && auth.isAuthenticated() && auth.getName() != null)
                ? auth.getName()
                : "ANONYMOUS";
        String roles = (auth != null && auth.getAuthorities() != null)
                ? auth.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.joining(","))
                : "";
        String clanId = TenantContext.getClanId();
        String remoteAddr = request.getRemoteAddr();

        log.warn("SECURITY VIOLATION [NFR-01]: Blocked HTTP {} on immutable ledger path [{}] "
                        + "by actor={} roles=[{}] clan={} ip={}",
                method, path, actor, roles, clanId, remoteAddr);
    }

    private static void writeMethodNotAllowedResponse(HttpServletResponse response, String method)
            throws IOException {
        response.setHeader("Allow", ALLOWED_METHODS_HEADER);
        response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("code", LedgerImmutabilityViolationException.ERROR_CODE);
        body.put("message", "Vi phạm bảo mật sổ cái bất biến (NFR-01): Hệ thống cấm tuyệt đối thao tác sửa/xóa (HTTP "
                + method + ") trên Sổ cái Quỹ chung. Mọi điều chỉnh bắt buộc thực hiện qua quy trình đề xuất bù trừ.");
        body.put("timestamp", Instant.now().toString());

        response.getWriter().write(JSON_MAPPER.writeValueAsString(body));
    }
}
