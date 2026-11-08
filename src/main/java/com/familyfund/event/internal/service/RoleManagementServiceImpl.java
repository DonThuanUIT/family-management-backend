package com.familyfund.event.internal.service;

import com.familyfund.core.exception.BadRequestException;
import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.core.security.SystemRoles;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.event.api.dto.AssignableRoleCode;
import com.familyfund.event.api.dto.EventRole;
import com.familyfund.event.api.dto.EventStatus;
import com.familyfund.event.api.dto.RoleActionType;
import com.familyfund.event.api.dto.RoleScopeType;
import com.familyfund.event.api.event.RoleChangedDomainEvent;
import com.familyfund.event.internal.dto.request.RoleAssignmentRequest;
import com.familyfund.event.internal.dto.request.RoleRevocationRequest;
import com.familyfund.event.internal.dto.response.*;
import com.familyfund.core.audit.entity.AuditLog;
import com.familyfund.event.internal.entity.Event;
import com.familyfund.event.internal.entity.EventPersonnel;
import com.familyfund.event.internal.entity.EventPersonnelId;
import com.familyfund.event.internal.repository.AuditLogRepository;
import com.familyfund.event.internal.repository.EventPersonnelRepository;
import com.familyfund.event.internal.repository.EventRepository;
import com.familyfund.event.internal.util.AuditIdGenerator;
import com.familyfund.genealogy.api.GenealogyMemberCommandService;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.genealogy.api.dto.BranchSummaryDto;
import com.familyfund.genealogy.api.dto.MemberSummaryDto;
import com.familyfund.identity.api.IdentityRoleCommandService;
import com.familyfund.identity.api.dto.AccountSummaryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleManagementServiceImpl implements RoleManagementService {

    private final AuditLogRepository auditLogRepository;
    private final EventRepository eventRepository;
    private final EventPersonnelRepository eventPersonnelRepository;
    private final AuditIdGenerator auditIdGenerator;
    private final CurrentUserProvider currentUserProvider;
    private final ApplicationEventPublisher eventPublisher;
    private final GenealogyQueryService genealogyQueryService;
    private final GenealogyMemberCommandService genealogyMemberCommandService;
    private final IdentityRoleCommandService identityRoleCommandService;

    @Override
    @Transactional
    public RoleAssignmentResponse assignRole(RoleAssignmentRequest request) {
        validateCallerAuthorization();
        String clanId = TenantContext.getClanId();
        String actorAccountId = currentUserProvider.currentAccountId()
                .orElseThrow(() -> new BadRequestException("Không xác định được danh tính tài khoản thực hiện"));

        validateRequestScope(request.roleCode(), request.scopeType());

        String targetMemberId = request.memberId().trim();
        String scopeId = request.scopeId().trim();

        // 1. Validate target member belongs to clan
        MemberSummaryDto member = genealogyQueryService.findMemberSummary(targetMemberId)
                .orElseThrow(() -> new ResourceNotFoundException("Thành viên", "memberId", targetMemberId));
        if (member.clanId() != null && !clanId.equals(member.clanId())) {
            throw new BadRequestException("Thành viên '" + targetMemberId + "' không thuộc dòng họ '" + clanId + "'");
        }

        String scopeName;
        String targetAccountId = null;

        if (request.scopeType() == RoleScopeType.BRANCH) {
            // BRANCH Scope: ROLE_BRANCH_HEAD
            BranchSummaryDto branch = genealogyQueryService.findBranchByIdAndClanId(scopeId, clanId)
                    .orElseThrow(() -> new ResourceNotFoundException("Nhánh gia phả", "branchId", scopeId));
            scopeName = branch.branchName();

            // Require active user account for Branch Head
            AccountSummaryDto account = identityRoleCommandService.requireActiveAccountByMemberId(targetMemberId, clanId);
            targetAccountId = account.accountId();

            // Assign branch representative in genealogy
            Optional<String> previousRepIdOpt = genealogyMemberCommandService.assignBranchRepresentative(scopeId, clanId, targetMemberId);

            // If there was a previous representative and they no longer represent any branch in the clan, revoke their ROLE_BRANCH_HEAD
            if (previousRepIdOpt.isPresent()) {
                String previousRepId = previousRepIdOpt.get();
                boolean stillRep = genealogyQueryService.isMemberRepresentativeOfAnyBranch(previousRepId, clanId);
                if (!stillRep) {
                    identityRoleCommandService.findAccountByMemberId(previousRepId)
                            .ifPresent(oldAcc -> identityRoleCommandService.revokeRoleFromAccount(oldAcc.accountId(), "ROLE_BRANCH_HEAD"));
                }
            }

            // Assign ROLE_BRANCH_HEAD to new account
            identityRoleCommandService.assignRoleToAccount(targetAccountId, "ROLE_BRANCH_HEAD");

        } else {
            // EVENT Scope: HeadOfOrganizer, OrganizerMember, EventTreasurer
            Event event = eventRepository.findByEventIdAndClanId(scopeId, clanId)
                    .orElseThrow(() -> new ResourceNotFoundException("Sự kiện", "eventId", scopeId));
            if (event.getStatus() == EventStatus.Closed) {
                throw new BusinessRuleException("Sự kiện đã đóng sổ (Closed). Không thể bổ nhiệm nhân sự.");
            }
            scopeName = event.getEventName();

            EventRole eventRole = EventRole.valueOf(request.roleCode().getCode());
            if (eventRole == null) {
                throw new BadRequestException("Vai trò sự kiện không hợp lệ: " + request.roleCode());
            }

            EventPersonnel personnel = eventPersonnelRepository.findById(new EventPersonnelId(scopeId, targetMemberId, eventRole))
                    .orElseGet(() -> EventPersonnel.builder()
                            .eventId(scopeId)
                            .memberId(targetMemberId)
                            .eventRole(eventRole)
                            .isReadOnly(false)
                            .build());
            personnel.setIsReadOnly(false);
            eventPersonnelRepository.save(personnel);

            Optional<AccountSummaryDto> accountOpt = identityRoleCommandService.findAccountByMemberId(targetMemberId);
            if (accountOpt.isPresent()) {
                targetAccountId = accountOpt.get().accountId();
            }
        }

        // 2. Persist immutable AuditLog entry
        String auditId = auditIdGenerator.generate();
        Instant now = Instant.now();
        Map<String, Object> roleNewValue = createRoleAuditPayload(
                request.roleCode().getCode(),
                request.scopeType().name(),
                scopeId,
                scopeName,
                targetMemberId,
                targetAccountId
        );
        AuditLog auditLog = AuditLog.builder()
                .auditId(auditId)
                .clanId(clanId)
                .actorAccountId(actorAccountId)
                .targetMemberId(targetMemberId)
                .targetAccountId(targetAccountId)
                .actionType(RoleActionType.ASSIGN.name())
                .entityType("ROLE")
                .entityId(scopeId)
                .roleCode(request.roleCode().getCode())
                .scopeType(request.scopeType().name())
                .scopeId(scopeId)
                .scopeName(scopeName)
                .oldValue(null)
                .newValue(roleNewValue)
                .description(request.description())
                .createdAt(now)
                .build();
        auditLogRepository.save(auditLog);

        // 3. Publish RoleChangedDomainEvent for async notification dispatching
        eventPublisher.publishEvent(new RoleChangedDomainEvent(
                auditId,
                clanId,
                actorAccountId,
                targetMemberId,
                targetAccountId,
                RoleActionType.ASSIGN,
                request.roleCode(),
                request.scopeType(),
                scopeId,
                scopeName,
                request.description(),
                now
        ));

        return new RoleAssignmentResponse(
                auditId,
                RoleActionType.ASSIGN.name(),
                request.roleCode().getCode(),
                request.roleCode().getDisplayName(),
                request.scopeType().name(),
                scopeId,
                scopeName,
                targetMemberId,
                member.fullName(),
                targetAccountId,
                actorAccountId,
                request.description(),
                now
        );
    }

    @Override
    @Transactional
    public RoleAssignmentResponse revokeRole(RoleRevocationRequest request) {
        validateCallerAuthorization();
        String clanId = TenantContext.getClanId();
        String actorAccountId = currentUserProvider.currentAccountId()
                .orElseThrow(() -> new BadRequestException("Không xác định được danh tính tài khoản thực hiện"));

        validateRequestScope(request.roleCode(), request.scopeType());

        String targetMemberId = request.memberId().trim();
        String scopeId = request.scopeId().trim();

        MemberSummaryDto member = genealogyQueryService.findMemberSummary(targetMemberId)
                .orElseThrow(() -> new ResourceNotFoundException("Thành viên", "memberId", targetMemberId));
        if (member.clanId() != null && !clanId.equals(member.clanId())) {
            throw new BadRequestException("Thành viên '" + targetMemberId + "' không thuộc dòng họ '" + clanId + "'");
        }

        String scopeName;
        String targetAccountId = null;

        if (request.scopeType() == RoleScopeType.BRANCH) {
            // BRANCH Scope: ROLE_BRANCH_HEAD
            BranchSummaryDto branch = genealogyQueryService.findBranchByIdAndClanId(scopeId, clanId)
                    .orElseThrow(() -> new ResourceNotFoundException("Nhánh gia phả", "branchId", scopeId));
            scopeName = branch.branchName();

            genealogyMemberCommandService.revokeBranchRepresentative(scopeId, clanId, targetMemberId);

            boolean stillRep = genealogyQueryService.isMemberRepresentativeOfAnyBranch(targetMemberId, clanId);
            Optional<AccountSummaryDto> accOpt = identityRoleCommandService.findAccountByMemberId(targetMemberId);
            if (accOpt.isPresent()) {
                targetAccountId = accOpt.get().accountId();
                if (!stillRep) {
                    identityRoleCommandService.revokeRoleFromAccount(targetAccountId, "ROLE_BRANCH_HEAD");
                }
            }

        } else {
            // EVENT Scope
            Event event = eventRepository.findByEventIdAndClanId(scopeId, clanId)
                    .orElseThrow(() -> new ResourceNotFoundException("Sự kiện", "eventId", scopeId));
            if (event.getStatus() == EventStatus.Closed) {
                throw new BusinessRuleException("Sự kiện đã đóng sổ (Closed). Không thể thu hồi nhân sự.");
            }
            scopeName = event.getEventName();

            EventRole eventRole = EventRole.valueOf(request.roleCode().getCode());
            if (eventRole == null) {
                throw new BadRequestException("Vai trò sự kiện không hợp lệ: " + request.roleCode());
            }

            eventPersonnelRepository.deleteById(new EventPersonnelId(scopeId, targetMemberId, eventRole));

            Optional<AccountSummaryDto> accountOpt = identityRoleCommandService.findAccountByMemberId(targetMemberId);
            if (accountOpt.isPresent()) {
                targetAccountId = accountOpt.get().accountId();
            }
        }

        // Persist AuditLog entry
        String auditId = auditIdGenerator.generate();
        Instant now = Instant.now();
        Map<String, Object> roleOldValue = createRoleAuditPayload(
                request.roleCode().getCode(),
                request.scopeType().name(),
                scopeId,
                scopeName,
                targetMemberId,
                targetAccountId
        );
        AuditLog auditLog = AuditLog.builder()
                .auditId(auditId)
                .clanId(clanId)
                .actorAccountId(actorAccountId)
                .targetMemberId(targetMemberId)
                .targetAccountId(targetAccountId)
                .actionType(RoleActionType.REVOKE.name())
                .entityType("ROLE")
                .entityId(scopeId)
                .roleCode(request.roleCode().getCode())
                .scopeType(request.scopeType().name())
                .scopeId(scopeId)
                .scopeName(scopeName)
                .oldValue(roleOldValue)
                .newValue(null)
                .description(request.reason())
                .createdAt(now)
                .build();
        auditLogRepository.save(auditLog);

        // Publish RoleChangedDomainEvent
        eventPublisher.publishEvent(new RoleChangedDomainEvent(
                auditId,
                clanId,
                actorAccountId,
                targetMemberId,
                targetAccountId,
                RoleActionType.REVOKE,
                request.roleCode(),
                request.scopeType(),
                scopeId,
                scopeName,
                request.reason(),
                now
        ));

        return new RoleAssignmentResponse(
                auditId,
                RoleActionType.REVOKE.name(),
                request.roleCode().getCode(),
                request.roleCode().getDisplayName(),
                request.scopeType().name(),
                scopeId,
                scopeName,
                targetMemberId,
                member.fullName(),
                targetAccountId,
                actorAccountId,
                request.reason(),
                now
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditLogResponse> getAuditLogs(String memberId, String scopeType, String scopeId) {
        validateCallerAuthorization();
        String clanId = TenantContext.getClanId();

        List<AuditLog> logs;
        if (memberId != null && !memberId.isBlank()) {
            logs = auditLogRepository.findByClanIdAndTargetMemberIdOrderByCreatedAtDesc(clanId, memberId.trim());
        } else if (scopeType != null && !scopeType.isBlank() && scopeId != null && !scopeId.isBlank()) {
            logs = auditLogRepository.findByClanIdAndScopeTypeAndScopeIdOrderByCreatedAtDesc(clanId, scopeType.trim().toUpperCase(), scopeId.trim());
        } else {
            logs = auditLogRepository.findByClanIdOrderByCreatedAtDesc(clanId);
        }

        return logs.stream().map(logEntry -> {
            AssignableRoleCode code = AssignableRoleCode.fromCode(logEntry.getRoleCode());
            String roleDisplayName = code != null ? code.getDisplayName() : logEntry.getRoleCode();

            String targetFullName = genealogyQueryService.findMemberSummary(logEntry.getTargetMemberId())
                    .map(MemberSummaryDto::fullName)
                    .orElse(logEntry.getTargetMemberId());

            return new AuditLogResponse(
                    logEntry.getAuditId(),
                    logEntry.getClanId(),
                    logEntry.getActorAccountId(),
                    logEntry.getActorAccountId(),
                    logEntry.getTargetMemberId(),
                    targetFullName,
                    logEntry.getTargetAccountId(),
                    logEntry.getActionType(),
                    logEntry.getRoleCode(),
                    roleDisplayName,
                    logEntry.getScopeType(),
                    logEntry.getScopeId(),
                    logEntry.getScopeName(),
                    logEntry.getDescription(),
                    logEntry.getCreatedAt()
            );
        }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RoleManagementOverviewResponse getRoleManagementOverview() {
        validateCallerAuthorization();
        String clanId = TenantContext.getClanId();

        // 1. Branches with representative details
        List<BranchSummaryDto> allBranches = genealogyQueryService.findAllBranchesByClanId(clanId);
        List<BranchRoleOverviewDto> branchDtos = allBranches.stream().map(b -> {
            String repAccountId = null;
            if (b.representativeMemberId() != null) {
                repAccountId = identityRoleCommandService.findAccountByMemberId(b.representativeMemberId())
                        .map(AccountSummaryDto::accountId)
                        .orElse(null);
            }
            return new BranchRoleOverviewDto(
                    b.branchId(),
                    b.branchName(),
                    b.generationLevel(),
                    b.representativeMemberId(),
                    b.representativeFullName(),
                    repAccountId
            );
        }).toList();

        // 2. Members with active account status
        List<MemberSummaryDto> allMembers = genealogyQueryService.findAllAliveMembersByClanId(clanId);
        Map<String, String> branchNameMap = new HashMap<>();
        for (BranchSummaryDto b : allBranches) {
            branchNameMap.put(b.branchId(), b.branchName());
        }

        List<String> memberIds = allMembers.stream().map(MemberSummaryDto::memberId).toList();
        Map<String, AccountSummaryDto> accountMap = identityRoleCommandService.findAccountsByMemberIds(memberIds);

        List<MemberRoleOptionDto> memberDtos = allMembers.stream().map(m -> {
            AccountSummaryDto acc = accountMap.get(m.memberId());
            boolean hasActive = acc != null && "Active".equalsIgnoreCase(acc.accountStatus());
            String accId = acc != null ? acc.accountId() : null;
            String bName = m.branchId() != null ? branchNameMap.getOrDefault(m.branchId(), m.branchId()) : null;

            return new MemberRoleOptionDto(
                    m.memberId(),
                    m.fullName(),
                    m.branchId(),
                    bName,
                    hasActive,
                    accId
            );
        }).toList();

        return new RoleManagementOverviewResponse(clanId, branchDtos, memberDtos);
    }

    private void validateCallerAuthorization() {
        if (!currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD) && !currentUserProvider.hasAuthority(SystemRoles.ADMIN)) {
            throw new AccessDeniedException("Chỉ Trưởng tộc hoặc Quản trị hệ thống mới có quyền chỉ định và thu hồi vai trò");
        }
    }

    private void validateRequestScope(AssignableRoleCode roleCode, RoleScopeType scopeType) {
        if (roleCode == null || scopeType == null) {
            throw new BadRequestException("roleCode và scopeType không được để trống");
        }
        if (roleCode.getSupportedScope() != scopeType) {
            throw new BadRequestException("Vai trò '" + roleCode.getCode() + "' chỉ áp dụng cho phạm vi '" +
                    roleCode.getSupportedScope() + "', không thể gắn với phạm vi '" + scopeType + "'");
        }
    }

    private Map<String, Object> createRoleAuditPayload(
            String roleCode,
            String scopeType,
            String scopeId,
            String scopeName,
            String memberId,
            String accountId
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("roleCode", roleCode);
        payload.put("scopeType", scopeType);
        payload.put("scopeId", scopeId);
        payload.put("scopeName", scopeName);
        payload.put("memberId", memberId);
        if (accountId != null) {
            payload.put("accountId", accountId);
        }
        return payload;
    }
}
