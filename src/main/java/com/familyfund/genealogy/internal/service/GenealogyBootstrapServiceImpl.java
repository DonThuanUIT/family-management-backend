package com.familyfund.genealogy.internal.service;

import com.familyfund.genealogy.api.GenealogyBootstrapService;
import com.familyfund.genealogy.api.dto.ClanBootstrapCommand;
import com.familyfund.genealogy.api.dto.ClanBootstrapResult;
import com.familyfund.genealogy.internal.entity.Clan;
import com.familyfund.genealogy.internal.entity.FamilyBranch;
import com.familyfund.genealogy.internal.entity.Member;
import com.familyfund.genealogy.internal.repository.ClanRepository;
import com.familyfund.genealogy.internal.repository.FamilyBranchRepository;
import com.familyfund.genealogy.internal.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GenealogyBootstrapServiceImpl implements GenealogyBootstrapService {

    private final ClanRepository clanRepository;
    private final FamilyBranchRepository branchRepository;
    private final MemberRepository memberRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ClanBootstrapResult bootstrapClanAndCreator(ClanBootstrapCommand command) {
        String clanId = "CLAN_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Clan clan = Clan.builder()
                .clanId(clanId)
                .clanName(command.clanName().trim())
                .ancestorName(cleanString(command.ancestorName()))
                .originLocation(cleanString(command.originLocation()))
                .establishedYear(command.establishedYear())
                .description(cleanString(command.description()))
                .build();
        Clan savedClan = clanRepository.save(clan);

        String rootBranchId = "BR_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        FamilyBranch rootBranch = FamilyBranch.builder()
                .branchId(rootBranchId)
                .clanId(clanId)
                .branchName("Nhánh Gốc")
                .generationLevel(1)
                .parentBranch(null)
                .description("Nhánh gốc khởi tổ dòng họ " + savedClan.getClanName())
                .build();
        FamilyBranch savedBranch = branchRepository.save(rootBranch);

        Member ancestor = null;
        if (savedClan.getAncestorName() != null && !savedClan.getAncestorName().isBlank()) {
            String ancestorMemberId = "MEM_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            ancestor = Member.builder()
                    .memberId(ancestorMemberId)
                    .fullName(savedClan.getAncestorName())
                    .gender("Male")
                    .generation(1)
                    .status("Deceased")
                    .branch(savedBranch)
                    .isPlaceholder(true)
                    .build();
            ancestor = memberRepository.save(ancestor);
            savedBranch.setRepresentative(ancestor);
            branchRepository.save(savedBranch);
        }

        int creatorGen = command.creatorGeneration() != null && command.creatorGeneration() >= 1
                ? command.creatorGeneration()
                : (ancestor != null ? 2 : 1);

        String creatorMemberId = "MEM_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Member creator = Member.builder()
                .memberId(creatorMemberId)
                .fullName(command.creatorFullName().trim())
                .gender(command.creatorGender().trim())
                .generation(creatorGen)
                .dateOfBirth(command.creatorDateOfBirth())
                .status("Living")
                .branch(savedBranch)
                .isPlaceholder(false)
                .build();
        Member savedCreator = memberRepository.save(creator);

        if (ancestor == null) {
            savedBranch.setRepresentative(savedCreator);
            branchRepository.save(savedBranch);
        }

        log.info("Bootstrapped clan [{}] with root branch [{}], ancestor [{}], creator member [{}]",
                savedClan.getClanId(), savedBranch.getBranchId(),
                ancestor != null ? ancestor.getMemberId() : "NONE", savedCreator.getMemberId());

        return new ClanBootstrapResult(
                savedClan.getClanId(),
                savedClan.getClanName(),
                savedClan.getAncestorName(),
                savedClan.getOriginLocation(),
                savedClan.getEstablishedYear(),
                savedClan.getDescription(),
                savedBranch.getBranchId(),
                savedBranch.getBranchName(),
                savedCreator.getMemberId(),
                savedCreator.getFullName(),
                savedCreator.getGender(),
                savedCreator.getGeneration(),
                savedCreator.getDateOfBirth()
        );
    }

    private String cleanString(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        return input.trim();
    }
}
