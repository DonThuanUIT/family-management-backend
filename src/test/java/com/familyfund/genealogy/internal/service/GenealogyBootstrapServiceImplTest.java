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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

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
    @DisplayName("Normal: bootstrapClanAndCreator with ancestorName and explicit generation should create ancestor and creator members")
    void bootstrap_WithAncestor_ExplicitGeneration_Success() {
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
        assertThat(result.originLocation()).isEqualTo("Hải Dương");
        assertThat(result.establishedYear()).isEqualTo(1850);
        assertThat(result.description()).isEqualTo("Chi phái Mộ Trạch");
        assertThat(result.rootBranchId()).startsWith("BR_");
        assertThat(result.rootBranchName()).isEqualTo("Nhánh Gốc");
        assertThat(result.creatorMemberId()).startsWith("MEM_");
        assertThat(result.creatorFullName()).isEqualTo("Vũ Văn Bình");
        assertThat(result.creatorGender()).isEqualTo("Male");
        assertThat(result.creatorGeneration()).isEqualTo(12);
        assertThat(result.creatorDateOfBirth()).isEqualTo(LocalDate.of(1988, 8, 8));

        verify(clanRepository, times(1)).save(any(Clan.class));
        verify(branchRepository, times(2)).save(any(FamilyBranch.class)); // 1: initial save, 2: assign ancestor representative

        ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository, times(2)).save(memberCaptor.capture());

        List<Member> savedMembers = memberCaptor.getAllValues();
        Member ancestorMember = savedMembers.get(0);
        assertThat(ancestorMember.getFullName()).isEqualTo("Cụ Tổ Vũ Hồn");
        assertThat(ancestorMember.getGender()).isEqualTo("Male");
        assertThat(ancestorMember.getGeneration()).isEqualTo(1);
        assertThat(ancestorMember.getStatus()).isEqualTo("Deceased");
        assertThat(ancestorMember.getIsPlaceholder()).isTrue();

        Member creatorMember = savedMembers.get(1);
        assertThat(creatorMember.getFullName()).isEqualTo("Vũ Văn Bình");
        assertThat(creatorMember.getGender()).isEqualTo("Male");
        assertThat(creatorMember.getGeneration()).isEqualTo(12);
        assertThat(creatorMember.getStatus()).isEqualTo("Living");
        assertThat(creatorMember.getIsPlaceholder()).isFalse();
    }

    @Test
    @DisplayName("Edge case: With ancestorName but null creatorGeneration should default creator generation to 2")
    void bootstrap_WithAncestor_NullGeneration_DefaultsTo2() {
        ClanBootstrapCommand command = new ClanBootstrapCommand(
                "Dòng họ Lê",
                "Lê Thái Tổ",
                "Thanh Hóa",
                1428,
                "Hoàng tộc Lam Sơn",
                "Lê Văn Nam",
                "Male",
                null,
                null
        );

        when(clanRepository.save(any(Clan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(branchRepository.save(any(FamilyBranch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ClanBootstrapResult result = bootstrapService.bootstrapClanAndCreator(command);

        assertThat(result).isNotNull();
        assertThat(result.ancestorName()).isEqualTo("Lê Thái Tổ");
        assertThat(result.creatorGeneration()).isEqualTo(2);

        ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository, times(2)).save(memberCaptor.capture());
        Member creator = memberCaptor.getAllValues().get(1);
        assertThat(creator.getGeneration()).isEqualTo(2);
    }

    @Test
    @DisplayName("Edge case: With ancestorName but generation <= 0 should default creator generation to 2")
    void bootstrap_WithAncestor_ZeroOrNegativeGeneration_DefaultsTo2() {
        ClanBootstrapCommand command = new ClanBootstrapCommand(
                "Dòng họ Lê",
                "Lê Thái Tổ",
                null,
                null,
                null,
                "Lê Văn Nam",
                "Male",
                0,
                null
        );

        when(clanRepository.save(any(Clan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(branchRepository.save(any(FamilyBranch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ClanBootstrapResult result = bootstrapService.bootstrapClanAndCreator(command);

        assertThat(result.creatorGeneration()).isEqualTo(2);
    }

    @Test
    @DisplayName("Normal: Without ancestorName and explicit generation should make creator the branch representative")
    void bootstrap_WithoutAncestor_ExplicitGeneration_Success() {
        ClanBootstrapCommand command = new ClanBootstrapCommand(
                "Dòng họ Đinh",
                null,
                "Ninh Bình",
                968,
                "Hoa Lư",
                "Đinh Tiên Hoàng",
                "Male",
                5,
                null
        );

        when(clanRepository.save(any(Clan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(branchRepository.save(any(FamilyBranch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ClanBootstrapResult result = bootstrapService.bootstrapClanAndCreator(command);

        assertThat(result).isNotNull();
        assertThat(result.ancestorName()).isNull();
        assertThat(result.creatorGeneration()).isEqualTo(5);

        // Only 1 member created (creator)
        verify(memberRepository, times(1)).save(any(Member.class));
        // Branch representative assigned to creator
        verify(branchRepository, times(2)).save(any(FamilyBranch.class));
    }

    @Test
    @DisplayName("Edge case: Without ancestorName and null generation should default creator generation to 1")
    void bootstrap_WithoutAncestor_NullGeneration_DefaultsTo1() {
        ClanBootstrapCommand command = new ClanBootstrapCommand(
                "Dòng họ Trần",
                null,
                null,
                null,
                null,
                "Trần Cảnh",
                "Male",
                null,
                null
        );

        when(clanRepository.save(any(Clan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(branchRepository.save(any(FamilyBranch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ClanBootstrapResult result = bootstrapService.bootstrapClanAndCreator(command);

        assertThat(result.ancestorName()).isNull();
        assertThat(result.creatorGeneration()).isEqualTo(1);
    }

    @Test
    @DisplayName("Edge case: Without ancestorName and negative generation should default creator generation to 1")
    void bootstrap_WithoutAncestor_NegativeGeneration_DefaultsTo1() {
        ClanBootstrapCommand command = new ClanBootstrapCommand(
                "Dòng họ Trần",
                null,
                null,
                null,
                null,
                "Trần Cảnh",
                "Male",
                -2,
                null
        );

        when(clanRepository.save(any(Clan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(branchRepository.save(any(FamilyBranch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ClanBootstrapResult result = bootstrapService.bootstrapClanAndCreator(command);

        assertThat(result.creatorGeneration()).isEqualTo(1);
    }

    @Test
    @DisplayName("Edge case: Blank string fields should be trimmed to null and NOT trigger ancestor placeholder creation")
    void bootstrap_BlankStrings_CleanedToNullAndBypassesAncestorCreation() {
        ClanBootstrapCommand command = new ClanBootstrapCommand(
                "   Dòng họ Lý   ",
                "      ",
                "   ",
                null,
                "      ",
                "   Lý Công Uẩn   ",
                "   Male   ",
                null,
                null
        );

        when(clanRepository.save(any(Clan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(branchRepository.save(any(FamilyBranch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ClanBootstrapResult result = bootstrapService.bootstrapClanAndCreator(command);

        assertThat(result.clanName()).isEqualTo("Dòng họ Lý");
        assertThat(result.ancestorName()).isNull();
        assertThat(result.originLocation()).isNull();
        assertThat(result.description()).isNull();
        assertThat(result.creatorFullName()).isEqualTo("Lý Công Uẩn");
        assertThat(result.creatorGender()).isEqualTo("Male");
        assertThat(result.creatorGeneration()).isEqualTo(1); // Defaulted to 1 since ancestorName is blank

        // Verify only creator member was saved
        verify(memberRepository, times(1)).save(any(Member.class));

        ArgumentCaptor<Clan> clanCaptor = ArgumentCaptor.forClass(Clan.class);
        verify(clanRepository).save(clanCaptor.capture());
        Clan savedClan = clanCaptor.getValue();
        assertThat(savedClan.getClanName()).isEqualTo("Dòng họ Lý");
        assertThat(savedClan.getAncestorName()).isNull();
        assertThat(savedClan.getOriginLocation()).isNull();
        assertThat(savedClan.getDescription()).isNull();
    }

    @Test
    @DisplayName("Edge case: Valid ancestor name with surrounding whitespace should be trimmed properly")
    void bootstrap_AncestorNameTrimming() {
        ClanBootstrapCommand command = new ClanBootstrapCommand(
                "Dòng họ Nguyễn",
                "   Nguyễn Trãi   ",
                "Chí Linh",
                1380,
                null,
                "Nguyễn Phi Khanh",
                "Male",
                1,
                null
        );

        when(clanRepository.save(any(Clan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(branchRepository.save(any(FamilyBranch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ClanBootstrapResult result = bootstrapService.bootstrapClanAndCreator(command);

        assertThat(result.ancestorName()).isEqualTo("Nguyễn Trãi");

        ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository, times(2)).save(memberCaptor.capture());
        Member ancestor = memberCaptor.getAllValues().get(0);
        assertThat(ancestor.getFullName()).isEqualTo("Nguyễn Trãi");
    }
}
