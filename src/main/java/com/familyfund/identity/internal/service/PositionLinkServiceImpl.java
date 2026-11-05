package com.familyfund.identity.internal.service;

import com.familyfund.core.exception.BadRequestException;
import com.familyfund.core.exception.DuplicateResourceException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.core.security.SystemRoles;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.genealogy.api.ClaimVirtualMemberCommand;
import com.familyfund.genealogy.api.CreateVirtualMemberCommand;
import com.familyfund.genealogy.api.GenealogyMemberCommandService;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.genealogy.api.dto.VirtualMemberDto;
import com.familyfund.identity.api.PositionLinkService;
import com.familyfund.identity.api.dto.ClaimPositionRequest;
import com.familyfund.identity.api.dto.ClaimPositionResponse;
import com.familyfund.identity.api.dto.CreateVirtualMemberRequest;
import com.familyfund.identity.api.dto.CreateVirtualMemberResponse;
import com.familyfund.identity.api.dto.InviteCodeResponse;
import com.familyfund.identity.api.dto.PositionLinkPreviewResponse;
import com.familyfund.identity.internal.entity.AccountRole;
import com.familyfund.identity.internal.entity.AccountRoleId;
import com.familyfund.identity.internal.entity.AccountStatus;
import com.familyfund.identity.internal.entity.InvitationCode;
import com.familyfund.identity.internal.entity.InviteType;
import com.familyfund.identity.internal.entity.UserAccount;
import com.familyfund.identity.internal.repository.AccountRoleRepository;
import com.familyfund.identity.internal.repository.InvitationCodeRepository;
import com.familyfund.identity.internal.repository.UserAccountRepository;
import com.familyfund.identity.internal.util.InviteCodeGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

/**
 * Creates virtual genealogy positions with a one-time {@code PositionLinkCode} (US-18) and redeems that code so the
 * recipient owns the position without any approval step (US-19).
 *
 * <p>This service lives in {@code identity} because {@code invitation_codes} belongs to this bounded context and
 * the dependency direction is fixed by the Modulith rules: {@code identity -> genealogy::api} is allowed,
 * {@code genealogy -> identity} is not. The member itself is created and claimed through the genealogy public
 * command contract, so the member invariants (tenant scoping, branch existence, role scope, generation, duplicate
 * profile, placeholder flag) stay in the module that owns table {@code members}.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PositionLinkServiceImpl implements PositionLinkService {

    /** Valid while the position is still unclaimed; keeps a leaked link from living forever. */
    static final int DEFAULT_VALIDITY_DAYS = 7;
    /** A position link is a single-use invitation. */
    static final int ONE_TIME_USE = 1;
    static final int MAX_CODE_ATTEMPTS = 5;
    /** family_branches.branch_id is VARCHAR(20). */
    static final int BRANCH_ID_MAX_LENGTH = 20;

    private final InvitationCodeRepository invitationCodeRepository;
    private final UserAccountRepository userAccountRepository;
    private final AccountRoleRepository accountRoleRepository;
    private final GenealogyMemberCommandService genealogyMemberCommandService;
    private final GenealogyQueryService genealogyQueryService;
    private final InviteCodeGenerator inviteCodeGenerator;
    private final CurrentUserProvider currentUserProvider;
    private final PasswordEncoder passwordEncoder;

    // ------------------------------------------------------------------ US-18: issue the code

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreateVirtualMemberResponse createVirtualMemberWithPositionLink(String rawBranchId,
                                                                          CreateVirtualMemberRequest request) {
        String clanId = TenantContext.getClanId();
        // 1. Path variable must satisfy family_branches.branch_id VARCHAR(20) before any lookup (400)
        String branchId = normalizeBranchId(rawBranchId);

        // 2. invitation_codes.created_by_account_id is NOT NULL, so an authenticated caller without an account
        //    identity cannot own the code (403)
        String accountId = currentUserProvider.currentAccountId()
                .orElseThrow(() -> new AccessDeniedException(
                        "Caller is not linked to a user account and cannot issue a position link code"));

        // 3. Resolve the link expiry before the first write (400 on a non-future timestamp)
        Instant expiresAt = resolveExpiresAt(request);

        // 4. Placeholder profile first: genealogy enforces branch existence (404), role scope (403),
        //    generation invariant (400) and the clan-wide duplicate profile guard (409).
        VirtualMemberDto member = genealogyMemberCommandService.createVirtualMember(CreateVirtualMemberCommand.builder()
                .clanId(clanId)
                .branchId(branchId)
                .fullName(request.fullName())
                .dateOfBirth(request.dateOfBirth())
                .gender(request.gender())
                .generation(request.generation())
                .status(request.status())
                .deathDateLunar(trimToNull(request.deathDateLunar()))
                .build());

        // 5. Bind exactly one one-time code to that position. target_member_id is UNIQUE, so a position can never
        //    end up with two codes; invite_code stays the PRIMARY KEY of the table.
        String inviteCode = generateUniqueInviteCode();
        InvitationCode saved = invitationCodeRepository.saveAndFlush(InvitationCode.builder()
                .inviteCode(inviteCode)
                .clanId(clanId)
                .inviteType(InviteType.PositionLinkCode)
                .shareableUrl(inviteCodeGenerator.buildPositionLinkUrl(inviteCode))
                .targetBranchId(branchId)
                .targetMemberId(member.memberId())
                .maxUses(ONE_TIME_USE)
                .usedCount(0)
                .expiresAt(expiresAt)
                .isActive(true)
                .createdByAccountId(accountId)
                .build());

        log.info("Đã tạo hồ sơ ảo và mã liên kết vị trí: inviteCode={}, memberId={}, branchId={}, clanId={}, expiresAt={}",
                saved.getInviteCode(), member.memberId(), branchId, clanId, expiresAt);

        return new CreateVirtualMemberResponse(member, toInviteCodeResponse(saved, clanId, member.branchName()));
    }

    // ------------------------------------------------------------------ US-19: redeem the code

    @Override
    public PositionLinkPreviewResponse getPositionLinkPreview(String rawInviteCode) {
        InvitationCode invitation = loadClaimablePositionLink(rawInviteCode);
        String clanId = invitation.getClanId();
        String branchId = invitation.getTargetBranchId();

        return new PositionLinkPreviewResponse(
                invitation.getInviteCode(),
                clanId,
                genealogyQueryService.getClanName(clanId).orElse(null),
                branchId,
                genealogyQueryService.getBranchName(clanId, branchId).orElse(null),
                invitation.getTargetMemberId(),
                genealogyQueryService.findMemberSummary(invitation.getTargetMemberId())
                        .map(member -> member.fullName())
                        .orElse(null),
                invitation.getExpiresAt()
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ClaimPositionResponse claimPosition(String rawInviteCode, ClaimPositionRequest request) {
        if (request == null) {
            throw new BadRequestException("Thông tin nhận vị trí phả hệ không được để trống");
        }

        // 1. The code must be a live, unused PositionLinkCode (404/400) - checked before the first write.
        InvitationCode invitation = loadClaimablePositionLink(rawInviteCode);
        String clanId = invitation.getClanId();
        String targetMemberId = invitation.getTargetMemberId();

        // 2. Who is redeeming? phone_number + password prove it; the account may not exist yet.
        String phoneNumber = trimToNull(request.phoneNumber());
        if (phoneNumber == null) {
            throw new BadRequestException("Số điện thoại không được để trống");
        }
        String password = request.password();
        if (password == null || password.isBlank()) {
            throw new BadRequestException("Mật khẩu không được để trống");
        }
        String email = normalizeEmail(request.email());

        UserAccount account = userAccountRepository.findByPhoneNumber(phoneNumber).orElse(null);
        if (account != null) {
            verifyRedeemer(account, password);
        }
        // A position is bound to one person: never link it to a second account, whatever the code says.
        if (userAccountRepository.findByMemberId(targetMemberId).isPresent()) {
            throw new DuplicateResourceException(
                    "Vị trí phả hệ " + targetMemberId + " đã được liên kết với một tài khoản khác");
        }
        if (account == null) {
            requireUnusedEmail(email);
        }

        // 3. The correct code is the proof of ownership: the position becomes a real profile with no moderator
        //    in between (placeholder -> regular profile, every other attribute untouched).
        VirtualMemberDto member = genealogyMemberCommandService.claimVirtualMember(ClaimVirtualMemberCommand.builder()
                .clanId(clanId)
                .memberId(targetMemberId)
                .build());

        // 4. Bind the account. An existing PendingApproval account (US-17 join flow) switches to Active here,
        //    which is the whole point of the feature: no verification step is required.
        UserAccount linked = account != null
                ? account
                : UserAccount.builder()
                        .accountId(generateAccountId())
                        .phoneNumber(phoneNumber)
                        .email(email)
                        .passwordHash(passwordEncoder.encode(password.trim()))
                        .build();
        linked.setMemberId(member.memberId());
        linked.setAccountStatus(AccountStatus.Active);
        UserAccount savedAccount = userAccountRepository.save(linked);

        // 5. A clan member is the default authority of anybody who owns a position.
        assignMemberRole(savedAccount.getAccountId());

        // 6. Spend the code: max_uses = 1 is now exhausted and the code stops being resolvable at all.
        invitation.setUsedCount((invitation.getUsedCount() != null ? invitation.getUsedCount() : 0) + 1);
        invitation.setIsActive(false);
        invitationCodeRepository.save(invitation);

        Instant linkedAt = Instant.now();
        log.info("Đã nhận vị trí bằng mã liên kết: inviteCode={}, memberId={}, accountId={}, branchId={}, clanId={}",
                invitation.getInviteCode(), member.memberId(), savedAccount.getAccountId(),
                member.branchId(), clanId);

        return new ClaimPositionResponse(
                savedAccount.getAccountId(),
                savedAccount.getPhoneNumber(),
                savedAccount.getEmail(),
                savedAccount.getAccountStatus().name(),
                Set.of(SystemRoles.MEMBER),
                member.memberId(),
                member.fullName(),
                member.generation(),
                member.branchId(),
                member.branchName(),
                clanId,
                genealogyQueryService.getClanName(clanId).orElse(null),
                invitation.getInviteCode(),
                linkedAt
        );
    }

    /**
     * Loads the position link code and asserts it can still be redeemed: right type, active, not expired, uses left
     * and bound to a position. Every rule is read-only, so nothing is written when one of them fails.
     */
    private InvitationCode loadClaimablePositionLink(String rawInviteCode) {
        // Shared gate with the onboarding flow: 404 for an unknown code or a general clan invite, and a distinct
        // 400 + code (INVITE_CODE_REVOKED / INVITE_CODE_EXPIRED / INVITE_CODE_EXHAUSTED) otherwise.
        return InviteCodeGuard.requireUsableForPositionLink(invitationCodeRepository, rawInviteCode);
    }

    /**
     * The phone number exists already: the password must match it, the account must still be usable and it must
     * not own another profile yet.
     */
    private void verifyRedeemer(UserAccount account, String password) {
        if (!passwordEncoder.matches(password.trim(), account.getPasswordHash())) {
            throw new BadRequestException("Số điện thoại hoặc mật khẩu không chính xác");
        }
        if (account.getAccountStatus() == AccountStatus.Suspended
                || account.getAccountStatus() == AccountStatus.Deactivated) {
            throw new AccessDeniedException("Tài khoản đang ở trạng thái " + account.getAccountStatus()
                    + " nên không thể nhận vị trí");
        }
        if (account.getMemberId() != null && !account.getMemberId().isBlank()) {
            throw new DuplicateResourceException("Tài khoản đã liên kết với hồ sơ phả hệ " + account.getMemberId());
        }
    }

    private void requireUnusedEmail(String email) {
        if (email != null && userAccountRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Email đã được đăng ký trong hệ thống");
        }
    }

    private void assignMemberRole(String accountId) {
        if (accountRoleRepository.existsByIdAccountIdAndIdRoleId(accountId, SystemRoles.MEMBER)) {
            return;
        }
        accountRoleRepository.save(AccountRole.builder()
                .id(new AccountRoleId(accountId, SystemRoles.MEMBER))
                .assignedAt(Instant.now())
                .build());
    }

    private static String generateAccountId() {
        // user_accounts.account_id is VARCHAR(20); same shape as the US-17 join flow.
        return "ACC_" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }

    /**
     * The branch id now comes from the request path, so it must be validated here instead of by Bean Validation
     * on the body: it cannot be blank and must fit {@code family_branches.branch_id VARCHAR(20)}.
     */
    private static String normalizeBranchId(String rawBranchId) {
        if (rawBranchId == null || rawBranchId.isBlank()) {
            throw new BadRequestException("Branch ID must not be blank");
        }
        String branchId = rawBranchId.trim();
        if (branchId.length() > BRANCH_ID_MAX_LENGTH) {
            throw new BadRequestException("Branch ID must not exceed " + BRANCH_ID_MAX_LENGTH + " characters");
        }
        return branchId;
    }

    private static Instant resolveExpiresAt(CreateVirtualMemberRequest request) {
        Instant now = Instant.now();
        if (request.expiresAt() != null) {
            if (!request.expiresAt().isAfter(now)) {
                throw new BadRequestException("Position link expiry must be in the future");
            }
            return request.expiresAt();
        }
        int days = request.validityDays() != null ? request.validityDays() : DEFAULT_VALIDITY_DAYS;
        return now.plus(days, ChronoUnit.DAYS);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeEmail(String email) {
        String normalized = trimToNull(email);
        return normalized == null ? null : normalized.toLowerCase();
    }

    private InviteCodeResponse toInviteCodeResponse(InvitationCode entity, String clanId, String branchName) {
        InviteCodeResponse response = InviteCodeResponse.fromEntity(entity);
        genealogyQueryService.getClanName(clanId).ifPresent(response::setClanName);
        response.setTargetBranchName(branchName);
        return response;
    }

    /**
     * Generates an invite_code not yet present in {@code invitation_codes}.
     * Layer 1: CSPRNG Base32; Layer 2: existsByInviteCode with bounded retries; Layer 3: PRIMARY KEY constraint.
     */
    String generateUniqueInviteCode() {
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String candidate = inviteCodeGenerator.generateCode();
            if (!invitationCodeRepository.existsByInviteCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate a unique position link code, please try again");
    }
}