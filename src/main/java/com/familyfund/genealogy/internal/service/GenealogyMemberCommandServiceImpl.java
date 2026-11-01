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

    @Override
    public String createMemberForExistingBranch(CreateOnboardingMemberCommand command) {
        if (command == null || command.clanId() == null || command.branchId() == null) {
            throw new BadRequestException("Thông tin gia nhập chi nhánh không hợp lệ");
        }

        FamilyBranch branch = familyBranchRepository.findByClanIdAndBranchId(command.clanId(), command.branchId())
                .orElseThrow(() -> new ResourceNotFoundException("Nhánh gia phả", "branchId", command.branchId()));

        String memberId = "MEM_" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();

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

        String branchId = "BR_" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
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
        String memberId = "MEM_" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();

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
}

