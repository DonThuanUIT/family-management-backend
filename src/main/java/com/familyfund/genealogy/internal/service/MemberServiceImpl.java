package com.familyfund.genealogy.internal.service;

import com.familyfund.core.exception.BadRequestException;
import com.familyfund.core.exception.DuplicateResourceException;
import com.familyfund.core.exception.InvalidGenerationException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.core.security.SystemRoles;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.genealogy.api.event.MemberLifeStatusChangedEvent;
import com.familyfund.genealogy.internal.dto.request.CreateMemberRequest;
import com.familyfund.genealogy.internal.dto.request.MemberSearchCriteria;
import com.familyfund.genealogy.internal.dto.request.UpdateLifeStatusRequest;
import com.familyfund.genealogy.internal.dto.response.MemberResponse;
import com.familyfund.genealogy.internal.entity.FamilyBranch;
import com.familyfund.genealogy.internal.entity.Member;
import com.familyfund.genealogy.internal.entity.MemberPrivacySetting;
import com.familyfund.genealogy.internal.mapper.MemberMapper;
import com.familyfund.genealogy.internal.repository.ClanRepository;
import com.familyfund.genealogy.internal.repository.FamilyBranchRepository;
import com.familyfund.genealogy.internal.repository.MemberPrivacySettingRepository;
import com.familyfund.genealogy.internal.repository.MemberRepository;
import com.familyfund.genealogy.internal.security.MemberPrivacyPolicy;
import com.familyfund.genealogy.internal.security.PrivacyEvaluationContext;
import com.familyfund.genealogy.internal.util.MemberIdGenerator;
import com.familyfund.genealogy.internal.util.VietnameseLunarCalendarConverter;
import com.familyfund.genealogy.internal.util.VietnameseLunarDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {

    static final int MAX_MEMBER_ID_ATTEMPTS = 5;
    /** family_branches.branch_id / members.branch_id are VARCHAR(20). */
    static final int BRANCH_ID_MAX_LENGTH = 20;

    private final MemberRepository memberRepository;
    private final FamilyBranchRepository branchRepository;
    private final ClanRepository clanRepository;
    private final MemberMapper memberMapper;
    private final MemberIdGenerator memberIdGenerator;
    private final CurrentUserProvider currentUserProvider;
    private final MemberPrivacySettingRepository privacySettingRepository;
    private final MemberPrivacyPolicy memberPrivacyPolicy;
    private final GenealogyQueryService genealogyQueryService;
    private final VietnameseLunarCalendarConverter lunarCalendarConverter;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemberResponse createMember(String rawBranchId, CreateMemberRequest request) {
        String clanId = TenantContext.getClanId();
        String branchId = normalizeBranchId(rawBranchId);
        String fullName = request.fullName().trim();

        // 0. Serialize member creation per clan (SELECT ... FOR UPDATE on clans) so the duplicate
        //    check below cannot race with a concurrent insert of the same profile.
        clanRepository.findByIdForUpdate(clanId)
                .orElseThrow(() -> new ResourceNotFoundException("Clan not found with ID: " + clanId));

        // 1. Branch must exist inside the current clan (FK members.branch_id -> family_branches)
        FamilyBranch branch = branchRepository.findByClanIdAndBranchId(clanId, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found with ID: " + branchId + " in clan: " + clanId));

        // 2. Role scope: CLAN_HEAD/ADMIN -> any branch; BRANCH_HEAD -> only the branch they represent
        authorizeBranchScope(clanId, branchId);

        // 3. A member cannot belong to an older generation than its branch
        if (request.generation() < branch.getGenerationLevel()) {
            throw new InvalidGenerationException(
                    "Generation level mismatch: Member generation (" + request.generation() +
                            ") cannot be less than branch generation level (" + branch.getGenerationLevel() + ")"
            );
        }

        // 4. Duplicate profile guard within the clan
        if (isDuplicateProfile(clanId, fullName, request.gender(), request.generation(), request.dateOfBirth())) {
            throw new DuplicateResourceException(
                    "A member profile with the same full name, gender, generation and date of birth already exists in clan: " + clanId
            );
        }

        String deathDateLunar = request.deathDateLunar() != null && !request.deathDateLunar().trim().isEmpty()
                ? request.deathDateLunar().trim() : null;

        // 5. Profiles created by managers are placeholders until a user account claims them (US-18)
        Member member = Member.builder()
                .memberId(generateUniqueMemberId())
                .fullName(fullName)
                .dateOfBirth(request.dateOfBirth())
                .gender(request.gender())
                .generation(request.generation())
                .status(request.status() != null ? request.status() : Member.STATUS_LIVING)
                .deathDateLunar(deathDateLunar)
                .branch(branch)
                .isPlaceholder(true)
                .build();

        // saveAndFlush surfaces PK/FK/CHECK violations inside this transaction instead of at commit
        Member saved = memberRepository.saveAndFlush(member);
        log.info("Created placeholder member: clan={}, branch={}, memberId={}", clanId, branchId, saved.getMemberId());
        MemberResponse raw = memberMapper.toResponse(saved);
        PrivacyEvaluationContext ctx = buildPrivacyContext(clanId);
        return memberPrivacyPolicy.applyPrivacy(raw, null, ctx);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MemberResponse> getMembersByBranch(String rawBranchId) {
        String clanId = TenantContext.getClanId();
        String branchId = normalizeBranchId(rawBranchId);
        if (!branchRepository.existsByClanIdAndBranchId(clanId, branchId)) {
            throw new ResourceNotFoundException("Branch not found with ID: " + branchId + " in clan: " + clanId);
        }
        List<Member> members = memberRepository.findByBranch_ClanIdAndBranch_BranchId(clanId, branchId);
        return toFilteredResponses(members, clanId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MemberResponse> getMembersByClan() {
        String clanId = TenantContext.getClanId();
        List<Member> members = memberRepository.findByBranch_ClanId(clanId);
        return toFilteredResponses(members, clanId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MemberResponse> searchMembers(MemberSearchCriteria criteria) {
        String clanId = TenantContext.getClanId();
        MemberSearchCriteria clean = criteria != null ? criteria : MemberSearchCriteria.of(null, null, null, null, null, null);

        List<Member> members = memberRepository.searchMembers(
                clanId,
                clean.keyword(),
                clean.memberId(),
                clean.fullName(),
                clean.generation(),
                clean.branchId(),
                clean.status()
        );
        return toFilteredResponses(members, clanId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemberResponse updateLifeStatus(String rawMemberId, UpdateLifeStatusRequest request) {
        String clanId = TenantContext.getClanId();
        String memberId = rawMemberId != null ? rawMemberId.trim() : "";
        if (memberId.isEmpty()) {
            throw new BadRequestException("Member ID must not be blank");
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found with ID: " + memberId));

        if (!clanId.equals(member.getBranch().getClanId())) {
            throw new ResourceNotFoundException("Member not found with ID: " + memberId + " in clan: " + clanId);
        }

        // Kiểm tra phạm vi quyền hạn (Dual-RBAC & Branch Scoping)
        authorizeBranchScope(clanId, member.getBranch().getBranchId());

        if (request.isDeceased()) {
            LocalDate deathDate = request.deathDate();
            if (deathDate == null) {
                throw new BadRequestException("Ngày mất là bắt buộc khi cập nhật thành viên sang trạng thái 'Đã mất' (Deceased)");
            }
            if (deathDate.isAfter(LocalDate.now())) {
                throw new BadRequestException("Ngày mất không được nằm trong tương lai");
            }
            if (member.getDateOfBirth() != null && deathDate.isBefore(member.getDateOfBirth())) {
                throw new BadRequestException("Ngày mất (" + deathDate + ") không được diễn ra trước ngày sinh (" + member.getDateOfBirth() + ")");
            }

            // Tự động tính ngày giỗ âm lịch chuẩn Việt Nam
            VietnameseLunarDate lunar = lunarCalendarConverter.convertSolarToLunar(deathDate);
            member.setStatus(Member.STATUS_DECEASED);
            member.setDeathDate(deathDate);
            member.setDeathDateLunar(lunar != null ? lunar.toShortAnniversaryString() : null);
            member.setBurialPlace(request.burialPlace() != null && !request.burialPlace().isBlank() ? request.burialPlace().trim() : null);
            log.info("Member {} marked as Deceased. Solar death date: {}, Lunar anniversary: {}",
                    memberId, deathDate, member.getDeathDateLunar());
        } else {
            // Chuyển về Living (hiệu đính)
            member.setStatus(Member.STATUS_LIVING);
            member.setDeathDate(null);
            member.setDeathDateLunar(null);
            member.setBurialPlace(null);
            log.info("Member {} life status reverted to Living", memberId);
        }

        Member saved = memberRepository.save(member);

        // Bắn Domain Event để module identity xử lý khóa/kích hoạt tài khoản
        eventPublisher.publishEvent(new MemberLifeStatusChangedEvent(
                saved.getMemberId(),
                clanId,
                saved.getStatus(),
                saved.getDeathDate(),
                saved.getDeathDateLunar()
        ));

        List<MemberResponse> responses = toFilteredResponses(List.of(saved), clanId);
        return responses.isEmpty() ? memberMapper.toResponse(saved) : responses.get(0);
    }

    private PrivacyEvaluationContext buildPrivacyContext(String clanId) {
        String callerMemberId = currentUserProvider.currentMemberId().orElse(null);
        boolean isClanHead = currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD);

        Set<String> managedBranchIds = Collections.emptySet();
        if (callerMemberId != null && !isClanHead) {
            managedBranchIds = new HashSet<>(genealogyQueryService.getBranchIdsManagedByMember(clanId, callerMemberId));
        }

        return PrivacyEvaluationContext.builder()
                .callerMemberId(callerMemberId)
                .isClanHead(isClanHead)
                .managedBranchIds(managedBranchIds)
                .isAuthorizedOrganizer(false)
                .build();
    }

    private List<MemberResponse> toFilteredResponses(List<Member> members, String clanId) {
        if (members == null || members.isEmpty()) {
            return Collections.emptyList();
        }

        PrivacyEvaluationContext ctx = buildPrivacyContext(clanId);

        List<String> memberIds = members.stream().map(Member::getMemberId).toList();
        Map<String, MemberPrivacySetting> settingsByMemberId = privacySettingRepository
                .findByMemberIdIn(memberIds)
                .stream()
                .collect(Collectors.toMap(MemberPrivacySetting::getMemberId, s -> s));

        return members.stream()
                .map(m -> {
                    MemberResponse raw = memberMapper.toResponse(m);
                    MemberPrivacySetting setting = settingsByMemberId.get(m.getMemberId());
                    return memberPrivacyPolicy.applyPrivacy(raw, setting, ctx);
                })
                .toList();
    }

    /**
     * The branch now comes from the request path (POST /api/v1/branches/{branchId}/members), so it must be
     * validated here instead of by Bean Validation on the body: it cannot be blank and must fit
     * `family_branches.branch_id VARCHAR(20)`.
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

    private void authorizeBranchScope(String clanId, String branchId) {
        if (currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD) || currentUserProvider.hasAuthority(SystemRoles.ADMIN)) {
            return;
        }
        if (currentUserProvider.hasAuthority(SystemRoles.BRANCH_HEAD)) {
            String callerMemberId = currentUserProvider.currentMemberId()
                    .orElseThrow(() -> new AccessDeniedException("Branch head account is not linked to a member profile"));
            if (branchRepository.existsByClanIdAndBranchIdAndRepresentative_MemberId(clanId, branchId, callerMemberId)) {
                return;
            }
            throw new AccessDeniedException("Branch head may only add members to the branch they manage");
        }
        throw new AccessDeniedException("Insufficient role to add members");
    }

    private boolean isDuplicateProfile(String clanId, String fullName, String gender, Integer generation, LocalDate dateOfBirth) {
        if (dateOfBirth == null) {
            return memberRepository.existsByBranch_ClanIdAndFullNameIgnoreCaseAndGenderAndGenerationAndDateOfBirthIsNull(
                    clanId, fullName, gender, generation);
        }
        return memberRepository.existsByBranch_ClanIdAndFullNameIgnoreCaseAndGenderAndGenerationAndDateOfBirth(
                    clanId, fullName, gender, generation, dateOfBirth);
    }

    /**
     * Generates a member_id not yet present in `members`.
     * Layer 1: timestamp + CSPRNG suffix; Layer 2: existsByMemberId with bounded retries;
     * Layer 3: PRIMARY KEY constraint.
     */
    String generateUniqueMemberId() {
        for (int attempt = 0; attempt < MAX_MEMBER_ID_ATTEMPTS; attempt++) {
            String candidate = memberIdGenerator.generate();
            if (!memberRepository.existsByMemberId(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate a unique member ID, please try again");
    }
}
