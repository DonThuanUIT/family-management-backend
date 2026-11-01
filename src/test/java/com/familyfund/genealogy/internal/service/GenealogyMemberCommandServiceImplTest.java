package com.familyfund.genealogy.internal.service;

import com.familyfund.core.exception.BadRequestException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.genealogy.api.CreateOnboardingBranchCommand;
import com.familyfund.genealogy.api.CreateOnboardingMemberCommand;
import com.familyfund.genealogy.internal.entity.FamilyBranch;
import com.familyfund.genealogy.internal.entity.Member;
import com.familyfund.genealogy.internal.repository.FamilyBranchRepository;
import com.familyfund.genealogy.internal.repository.MemberRepository;
import com.familyfund.genealogy.internal.validator.GenealogyValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenealogyMemberCommandServiceImplTest {

    private static final String CLAN_ID = "CLAN_01";
    private static final String ROOT_BRANCH_ID = "BR_ROOT";

    @Mock
    private FamilyBranchRepository familyBranchRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private GenealogyValidator genealogyValidator;

    @InjectMocks
    private GenealogyMemberCommandServiceImpl commandService;

    private FamilyBranch mockRootBranch;

    @BeforeEach
    void setUp() {
        mockRootBranch = FamilyBranch.builder()
                .branchId(ROOT_BRANCH_ID)
                .clanId(CLAN_ID)
                .branchName("Chi Gốc")
                .generationLevel(1)
                .build();
    }

    @Test
    @DisplayName("createMemberForExistingBranch should save new member and return memberId")
    void createMemberForExistingBranch_Success() {
        CreateOnboardingMemberCommand command = CreateOnboardingMemberCommand.builder()
                .clanId(CLAN_ID)
                .branchId(ROOT_BRANCH_ID)
                .fullName("Nguyễn Văn A")
                .dateOfBirth(LocalDate.of(1995, 3, 15))
                .gender("Male")
                .generation(3)
                .build();

        when(familyBranchRepository.findByClanIdAndBranchId(CLAN_ID, ROOT_BRANCH_ID))
                .thenReturn(Optional.of(mockRootBranch));
        when(memberRepository.save(any(Member.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        String memberId = commandService.createMemberForExistingBranch(command);

        assertNotNull(memberId);
        ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(memberCaptor.capture());

        Member savedMember = memberCaptor.getValue();
        assertEquals("Nguyễn Văn A", savedMember.getFullName());
        assertEquals(LocalDate.of(1995, 3, 15), savedMember.getDateOfBirth());
        assertEquals("Male", savedMember.getGender());
        assertEquals(3, savedMember.getGeneration());
        assertEquals("Living", savedMember.getStatus());
        assertEquals(mockRootBranch, savedMember.getBranch());
        assertFalse(savedMember.getIsPlaceholder());
    }

    @Test
    @DisplayName("createMemberForExistingBranch with missing branch should throw ResourceNotFoundException")
    void createMemberForExistingBranch_BranchNotFound_ThrowsException() {
        CreateOnboardingMemberCommand command = CreateOnboardingMemberCommand.builder()
                .clanId(CLAN_ID)
                .branchId("UNKNOWN_BRANCH")
                .fullName("Nguyễn Văn A")
                .gender("Male")
                .generation(3)
                .build();

        when(familyBranchRepository.findByClanIdAndBranchId(CLAN_ID, "UNKNOWN_BRANCH"))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> commandService.createMemberForExistingBranch(command));
        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("createMemberForExistingBranch with invalid command should throw BadRequestException")
    void createMemberForExistingBranch_InvalidCommand_ThrowsBadRequest() {
        assertThrows(BadRequestException.class,
                () -> commandService.createMemberForExistingBranch(null));
    }

    @Test
    @DisplayName("createChildBranchAndMember should create child branch, member, and set representative")
    void createChildBranchAndMember_Success() {
        CreateOnboardingBranchCommand command = CreateOnboardingBranchCommand.builder()
                .clanId(CLAN_ID)
                .parentBranchId(ROOT_BRANCH_ID)
                .newBranchName("Chi Đệ Nhị")
                .memberFullName("Nguyễn Văn Trưởng Chi")
                .memberDateOfBirth(LocalDate.of(1990, 8, 20))
                .memberGender("Male")
                .memberGeneration(2)
                .build();

        when(familyBranchRepository.findByClanIdAndBranchId(CLAN_ID, ROOT_BRANCH_ID))
                .thenReturn(Optional.of(mockRootBranch));
        doNothing().when(genealogyValidator).validateNoCycles(CLAN_ID, null, ROOT_BRANCH_ID);

        when(familyBranchRepository.save(any(FamilyBranch.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(memberRepository.save(any(Member.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        String memberId = commandService.createChildBranchAndMember(command);

        assertNotNull(memberId);

        ArgumentCaptor<FamilyBranch> branchCaptor = ArgumentCaptor.forClass(FamilyBranch.class);
        verify(familyBranchRepository, org.mockito.Mockito.times(2)).save(branchCaptor.capture());

        FamilyBranch finalBranchState = branchCaptor.getAllValues().get(1);
        assertEquals("Chi Đệ Nhị", finalBranchState.getBranchName());
        assertEquals(2, finalBranchState.getGenerationLevel());
        assertEquals(mockRootBranch, finalBranchState.getParentBranch());
        assertNotNull(finalBranchState.getRepresentative());
        assertEquals("Nguyễn Văn Trưởng Chi", finalBranchState.getRepresentative().getFullName());

        ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(memberCaptor.capture());
        Member savedMember = memberCaptor.getValue();
        assertEquals("Nguyễn Văn Trưởng Chi", savedMember.getFullName());
        assertEquals(2, savedMember.getGeneration());
        assertEquals("Living", savedMember.getStatus());
    }

    @Test
    @DisplayName("createChildBranchAndMember with non-existent parent branch should throw ResourceNotFoundException")
    void createChildBranchAndMember_ParentNotFound_ThrowsException() {
        CreateOnboardingBranchCommand command = CreateOnboardingBranchCommand.builder()
                .clanId(CLAN_ID)
                .parentBranchId("UNKNOWN_PARENT")
                .newBranchName("Chi Đệ Nhị")
                .memberFullName("Nguyễn Văn B")
                .memberGender("Male")
                .memberGeneration(2)
                .build();

        when(familyBranchRepository.findByClanIdAndBranchId(CLAN_ID, "UNKNOWN_PARENT"))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> commandService.createChildBranchAndMember(command));
        verify(familyBranchRepository, never()).save(any());
        verify(memberRepository, never()).save(any());
    }
}

