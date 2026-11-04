package com.familyfund.genealogy.internal.service;

import com.familyfund.core.exception.BadRequestException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.genealogy.api.CreateOnboardingBranchCommand;
import com.familyfund.genealogy.api.CreateOnboardingMemberCommand;
import com.familyfund.genealogy.api.GenealogyMemberCommandService;
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
 * Implementation of GenealogyMemberCommandService handling member & branch creation for US-17 onboarding.
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
