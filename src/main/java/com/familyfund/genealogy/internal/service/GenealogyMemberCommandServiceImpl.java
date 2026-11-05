package com.familyfund.genealogy.internal.service;

import com.familyfund.core.exception.BadRequestException;
import com.familyfund.core.exception.DuplicateResourceException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.genealogy.api.ClaimVirtualMemberCommand;
import com.familyfund.genealogy.api.CreateOnboardingBranchCommand;
import com.familyfund.genealogy.api.CreateOnboardingMemberCommand;
import com.familyfund.genealogy.api.CreateVirtualMemberCommand;
import com.familyfund.genealogy.api.GenealogyMemberCommandService;
import com.familyfund.genealogy.api.dto.VirtualMemberDto;
import com.familyfund.genealogy.internal.dto.request.CreateMemberRequest;
import com.familyfund.genealogy.internal.dto.response.MemberResponse;
import com.familyfund.genealogy.internal.entity.FamilyBranch;
import com.familyfund.genealogy.internal.entity.Member;
import com.familyfund.genealogy.internal.repository.FamilyBranchRepository;
import com.familyfund.genealogy.internal.repository.MemberRepository;
import com.familyfund.genealogy.internal.util.BranchIdGenerator;
import com.familyfund.genealogy.internal.util.MemberIdGenerator;
import com.familyfund.genealogy.internal.validator.GenealogyValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Implementation of GenealogyMemberCommandService handling member & branch creation for US-17 onboarding,
 * virtual placeholder profiles for US-18 and the claiming of those placeholders for US-19.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class GenealogyMemberCommandServiceImpl implements GenealogyMemberCommandService {

    private final FamilyBranchRepository familyBranchRepository;
    private final MemberRepository memberRepository;
    private final GenealogyValidator genealogyValidator;
    private final BranchIdGenerator branchIdGenerator;
    private final MemberIdGenerator memberIdGenerator;
    private final MemberService memberService;

    @Override
    public String createMemberForExistingBranch(CreateOnboardingMemberCommand command) {
        if (command == null || command.clanId() == null || command.branchId() == null) {
            throw new BadRequestException("Thông tin gia nhập chi nhánh không hợp lệ");
        }

        FamilyBranch branch = familyBranchRepository.findByClanIdAndBranchId(command.clanId(), command.branchId())
                .orElseThrow(() -> new ResourceNotFoundException("Nhánh gia phả", "branchId", command.branchId()));

        String memberId = memberIdGenerator.generate();

        Member member = Member.builder()
                .memberId(memberId)
                .fullName(command.fullName().trim())
                .dateOfBirth(command.dateOfBirth())
                .gender(command.gender().trim())
                .generation(command.generation())
                .status("Living")
                .branch(branch)
                .isPlaceholder(false)
                .build();

        Member saved = memberRepository.save(member);
        log.info("Đã tạo thành viên mới từ đơn gia nhập: memberId={}, branchId={}, clanId={}",
                memberId, command.branchId(), command.clanId());
        return saved.getMemberId();
    }

    @Override
    public String createChildBranchAndMember(CreateOnboardingBranchCommand command) {
        if (command == null || command.clanId() == null || command.parentBranchId() == null) {
            throw new BadRequestException("Thông tin đề xuất tạo nhánh con không hợp lệ");
        }

        FamilyBranch parentBranch = familyBranchRepository.findByClanIdAndBranchId(command.clanId(), command.parentBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Nhánh cha", "parentBranchId", command.parentBranchId()));

        // Validate cycle prevention
        genealogyValidator.validateNoCycles(command.clanId(), null, command.parentBranchId());

        String branchId = branchIdGenerator.generate();
        int childGenerationLevel = parentBranch.getGenerationLevel() + 1;

        // 1. Create Child Branch
        FamilyBranch childBranch = FamilyBranch.builder()
                .branchId(branchId)
                .clanId(command.clanId())
                .branchName(command.newBranchName().trim())
                .generationLevel(childGenerationLevel)
                .parentBranch(parentBranch)
                .description("Nhánh con được tạo từ luồng Onboarding gia nhập dòng họ")
                .build();

        FamilyBranch savedBranch = familyBranchRepository.save(childBranch);

        // 2. Create the first Member in this new branch
        String memberId = memberIdGenerator.generate();

        Member member = Member.builder()
                .memberId(memberId)
                .fullName(command.memberFullName().trim())
                .dateOfBirth(command.memberDateOfBirth())
                .gender(command.memberGender().trim())
                .generation(command.memberGeneration())
                .status("Living")
                .branch(savedBranch)
                .isPlaceholder(false)
                .build();

        Member savedMember = memberRepository.save(member);

        // 3. Assign this new member as representative of the newly created branch
        savedBranch.setRepresentative(savedMember);
        familyBranchRepository.save(savedBranch);

        log.info("Đã tạo nhánh con mới và thành viên đại diện: branchId={}, memberId={}, clanId={}",
                branchId, memberId, command.clanId());
        return savedMember.getMemberId();
    }

    /**
     * US-18: a virtual profile is a normal placeholder member (see {@link MemberService#createMember}), so the
     * tenant scoping, branch existence (404), role scope (403), generation invariant (400) and the clan-wide
     * duplicate profile guard (409) are reused instead of re-implemented here.
     */
    @Override
    public VirtualMemberDto createVirtualMember(CreateVirtualMemberCommand command) {
        if (command == null
                || command.clanId() == null || command.clanId().isBlank()
                || command.branchId() == null || command.branchId().isBlank()) {
            throw new BadRequestException("Thông tin hồ sơ ảo không hợp lệ");
        }

        // The tenant is the authority: a command for a different clan must never create a profile here.
        String clanId = command.clanId().trim();
        if (!clanId.equals(TenantContext.getClanId())) {
            throw new BadRequestException("Dòng họ trong ngữ cảnh không khớp với dòng họ của hồ sơ ảo");
        }

        MemberResponse created = memberService.createMember(command.branchId(), CreateMemberRequest.builder()
                .fullName(command.fullName())
                .dateOfBirth(command.dateOfBirth())
                .gender(command.gender())
                .generation(command.generation())
                .status(command.status())
                .deathDateLunar(command.deathDateLunar())
                .build());

        log.info("Đã tạo hồ sơ ảo (placeholder) cho vị trí lá: memberId={}, branchId={}, clanId={}",
                created.memberId(), created.branchId(), created.clanId());

        return new VirtualMemberDto(
                created.memberId(),
                created.fullName(),
                created.dateOfBirth(),
                created.gender(),
                created.generation(),
                created.status(),
                created.deathDateLunar(),
                created.branchId(),
                created.branchName(),
                created.clanId(),
                created.isPlaceholder(),
                created.createdAt()
        );
    }

    /**
     * US-19: the one-time position link code was redeemed, so the placeholder becomes the real profile of that
     * person. Only {@code is_placeholder} changes: name, generation, branch and everything already recorded against
     * this {@code member_id} (events, contributions, advances) stay exactly where they are.
     *
     * <p>The position is resolved by {@code clanId + memberId} instead of the {@code TenantContext} because the
     * redemption request is anonymous: the owning clan is the one stamped on the invitation code, not the one the
     * HTTP request happens to carry.</p>
     */
    @Override
    public VirtualMemberDto claimVirtualMember(ClaimVirtualMemberCommand command) {
        if (command == null
                || command.clanId() == null || command.clanId().isBlank()
                || command.memberId() == null || command.memberId().isBlank()) {
            throw new BadRequestException("Thông tin nhận vị trí phả hệ không hợp lệ");
        }

        String clanId = command.clanId().trim();
        String memberId = command.memberId().trim();

        Member member = memberRepository.findByBranch_ClanIdAndMemberId(clanId, memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Hồ sơ phả hệ", "memberId", memberId));

        // A position is claimable exactly once: the code is one-time and the profile stops being a placeholder here.
        if (!Boolean.TRUE.equals(member.getIsPlaceholder())) {
            throw new DuplicateResourceException(
                    "Vị trí phả hệ " + memberId + " đã được nhận trước đó nên không thể liên kết thêm tài khoản");
        }

        member.setIsPlaceholder(false);
        Member saved = memberRepository.saveAndFlush(member);

        FamilyBranch branch = saved.getBranch();
        log.info("Đã nhận vị trí ảo (placeholder -> hồ sơ thật): memberId={}, branchId={}, clanId={}",
                saved.getMemberId(), branch.getBranchId(), clanId);

        return new VirtualMemberDto(
                saved.getMemberId(),
                saved.getFullName(),
                saved.getDateOfBirth(),
                saved.getGender(),
                saved.getGeneration(),
                saved.getStatus(),
                saved.getDeathDateLunar(),
                branch.getBranchId(),
                branch.getBranchName(),
                branch.getClanId(),
                saved.getIsPlaceholder(),
                saved.getCreatedAt()
        );
    }

    @Override
    public java.util.Optional<String> assignBranchRepresentative(String branchId, String clanId, String newRepresentativeMemberId) {
        if (branchId == null || branchId.isBlank() || clanId == null || clanId.isBlank() || newRepresentativeMemberId == null || newRepresentativeMemberId.isBlank()) {
            throw new BadRequestException("branchId, clanId và newRepresentativeMemberId không được để trống");
        }

        FamilyBranch branch = familyBranchRepository.findByClanIdAndBranchId(clanId.trim(), branchId.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Nhánh gia phả", "branchId", branchId));

        Member newRepresentative = memberRepository.findByBranch_ClanIdAndMemberId(clanId.trim(), newRepresentativeMemberId.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Thành viên", "memberId", newRepresentativeMemberId));

        if (!"Living".equalsIgnoreCase(newRepresentative.getStatus())) {
            throw new BadRequestException("Chỉ thành viên còn sống mới có thể đảm nhận vai trò Trưởng nhánh");
        }

        String previousRepresentativeId = branch.getRepresentative() != null ? branch.getRepresentative().getMemberId() : null;

        branch.setRepresentative(newRepresentative);
        familyBranchRepository.save(branch);

        log.info("Assigned representative [{}] to branch [{}] in clan [{}]. Previous representative was [{}]",
                newRepresentativeMemberId, branchId, clanId, previousRepresentativeId);

        if (previousRepresentativeId != null && !previousRepresentativeId.equals(newRepresentativeMemberId)) {
            return java.util.Optional.of(previousRepresentativeId);
        }
        return java.util.Optional.empty();
    }

    @Override
    public void revokeBranchRepresentative(String branchId, String clanId, String targetMemberId) {
        if (branchId == null || branchId.isBlank() || clanId == null || clanId.isBlank()) {
            throw new BadRequestException("branchId và clanId không được để trống");
        }

        FamilyBranch branch = familyBranchRepository.findByClanIdAndBranchId(clanId.trim(), branchId.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Nhánh gia phả", "branchId", branchId));

        if (branch.getRepresentative() == null) {
            return;
        }

        if (targetMemberId != null && !targetMemberId.isBlank()) {
            if (!targetMemberId.trim().equals(branch.getRepresentative().getMemberId())) {
                throw new BadRequestException("Thành viên '" + targetMemberId + "' không phải là Trưởng nhánh hiện tại của nhánh '" + branch.getBranchName() + "'");
            }
        }

        branch.setRepresentative(null);
        familyBranchRepository.save(branch);

        log.info("Revoked representative from branch [{}] in clan [{}]", branchId, clanId);
    }
}
