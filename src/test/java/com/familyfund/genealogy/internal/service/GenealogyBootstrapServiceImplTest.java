package com.familyfund.genealogy.internal.service;

import com.familyfund.genealogy.api.dto.ClanBootstrapCommand;
import com.familyfund.genealogy.api.dto.ClanBootstrapResult;
import com.familyfund.genealogy.internal.entity.Clan;
import com.familyfund.genealogy.internal.entity.FamilyBranch;
import com.familyfund.genealogy.internal.entity.Member;
import com.familyfund.genealogy.internal.repository.ClanRepository;
import com.familyfund.genealogy.internal.repository.FamilyBranchRepository;
import com.familyfund.genealogy.internal.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenealogyBootstrapServiceImplTest {

    @Mock
    private ClanRepository clanRepository;

    @Mock
    private FamilyBranchRepository branchRepository;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private GenealogyBootstrapServiceImpl bootstrapService;

    @Test
    @DisplayName("bootstrapClanAndCreator with ancestorName should create ancestor and creator members")
    void bootstrapWithAncestor_Success() {
        ClanBootstrapCommand command = new ClanBootstrapCommand(
                "Dòng họ Vũ",
                "Cụ Tổ Vũ Hồn",
                "Hải Dương",
                1850,
                "Chi phái Mộ Trạch",
                "Vũ Văn Bình",
                "Male",
                12,
                LocalDate.of(1988, 8, 8)
        );

        when(clanRepository.save(any(Clan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(branchRepository.save(any(FamilyBranch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ClanBootstrapResult result = bootstrapService.bootstrapClanAndCreator(command);

        assertThat(result).isNotNull();
        assertThat(result.clanId()).startsWith("CLAN_");
        assertThat(result.clanName()).isEqualTo("Dòng họ Vũ");
        assertThat(result.ancestorName()).isEqualTo("Cụ Tổ Vũ Hồn");
        assertThat(result.rootBranchId()).startsWith("BR_");
        assertThat(result.creatorMemberId()).startsWith("MEM_");
        assertThat(result.creatorFullName()).isEqualTo("Vũ Văn Bình");
        assertThat(result.creatorGeneration()).isEqualTo(12);

        verify(clanRepository, times(1)).save(any(Clan.class));
        verify(branchRepository, times(2)).save(any(FamilyBranch.class)); // initial save + assign representative
        verify(memberRepository, times(2)).save(any(Member.class)); // ancestor + creator
    }

    @Test
    @DisplayName("bootstrapClanAndCreator without ancestorName should default creator generation to 1 and make creator the representative")
    void bootstrapWithoutAncestor_Success() {
        ClanBootstrapCommand command = new ClanBootstrapCommand(
                "Dòng họ Lê",
                null,
                "Thanh Hóa",
                1900,
                null,
                "Lê Lợi",
                "Male",
                null,
                LocalDate.of(1990, 1, 1)
        );

        when(clanRepository.save(any(Clan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(branchRepository.save(any(FamilyBranch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ClanBootstrapResult result = bootstrapService.bootstrapClanAndCreator(command);

        assertThat(result).isNotNull();
        assertThat(result.clanId()).startsWith("CLAN_");
        assertThat(result.clanName()).isEqualTo("Dòng họ Lê");
        assertThat(result.ancestorName()).isNull();
        assertThat(result.creatorGeneration()).isEqualTo(1); // defaulted to 1

        verify(clanRepository, times(1)).save(any(Clan.class));
        verify(branchRepository, times(2)).save(any(FamilyBranch.class)); // initial + representative assigned to creator
        verify(memberRepository, times(1)).save(any(Member.class)); // only creator
    }
}
