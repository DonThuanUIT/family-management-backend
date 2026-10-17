package com.familyfund.modules.genealogy;

import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.core.exception.InvalidGenerationException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.modules.genealogy.dto.request.CreateChildBranchRequest;
import com.familyfund.modules.genealogy.dto.request.UpdateBranchRequest;
import com.familyfund.modules.genealogy.dto.response.BranchDetailResponse;
import com.familyfund.modules.genealogy.dto.response.BranchTreeNodeResponse;
import com.familyfund.modules.genealogy.entity.FamilyBranch;
import com.familyfund.modules.genealogy.entity.Member;
import com.familyfund.modules.genealogy.repository.FamilyBranchRepository;
import com.familyfund.modules.genealogy.repository.MemberRepository;
import com.familyfund.modules.genealogy.service.impl.GenealogyServiceImpl;
import com.familyfund.modules.genealogy.validator.GenealogyValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GenealogyHierarchyTest {

    @Mock
    private FamilyBranchRepository branchRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private GenealogyValidator genealogyValidator;

    @InjectMocks
    private GenealogyServiceImpl genealogyService;

    private FamilyBranch rootBranch;
    private FamilyBranch childBranch;

    @BeforeEach
    void setUp() {
        rootBranch = FamilyBranch.builder()
                .branchId("BR_ROOT")
                .clanName("Nguyen Clan")
                .branchName("Dai Ton Branch")
                .generationLevel(1)
                .parentBranch(null)
                .build();

        childBranch = FamilyBranch.builder()
                .branchId("BR_CHILD_1")
                .clanName("Nguyen Clan")
                .branchName("Giap Branch")
                .generationLevel(2)
                .parentBranch(rootBranch)
                .build();
    }

    @Test
    @DisplayName("Create child branch successfully: Generation level automatically set to parent generation + 1")
    void testCreateChildBranch_Success() {
        CreateChildBranchRequest request = CreateChildBranchRequest.builder()
                .branchName("Giap Branch")
                .parentBranchId("BR_ROOT")
                .description("Giap branch located in Hanoi")
                .build();

        when(branchRepository.findById("BR_ROOT")).thenReturn(Optional.of(rootBranch));
        when(branchRepository.save(any(FamilyBranch.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BranchDetailResponse response = genealogyService.createChildBranch(request);

        assertNotNull(response);
        assertEquals("Giap Branch", response.getBranchName());
        assertEquals(2, response.getGenerationLevel());
        assertEquals("BR_ROOT", response.getParentBranchId());
        verify(genealogyValidator).kiemTraChuTrinh(null, "BR_ROOT");
        verify(genealogyValidator).kiemTraDoiThu(1, 2);
    }

    @Test
    @DisplayName("Create child branch fails when parent branch is not found")
    void testCreateChildBranch_ParentNotFound_ThrowsResourceNotFoundException() {
        CreateChildBranchRequest request = CreateChildBranchRequest.builder()
                .branchName("Giap Branch")
                .parentBranchId("BR_NON_EXIST")
                .build();

        when(branchRepository.findById("BR_NON_EXIST")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> genealogyService.createChildBranch(request));
        verify(branchRepository, never()).save(any());
    }

    @Test
    @DisplayName("Update reparenting branch successfully and triggers cascade generation level update")
    void testUpdateBranch_Reparenting_Success_WithCascade() {
        // New parent branch: BR_NEW_PARENT with generation = 2 -> childBranch will become generation = 3 (delta = +1)
        FamilyBranch newParent = FamilyBranch.builder()
                .branchId("BR_NEW_PARENT")
                .generationLevel(2)
                .build();

        // Grandchild branch: BR_GRANDCHILD under childBranch with generation = 3
        FamilyBranch grandChild = FamilyBranch.builder()
                .branchId("BR_GRANDCHILD")
                .generationLevel(3)
                .parentBranch(childBranch)
                .build();

        UpdateBranchRequest request = UpdateBranchRequest.builder()
                .branchName("Giap Branch Renamed")
                .parentBranchId("BR_NEW_PARENT")
                .build();

        when(branchRepository.findById("BR_CHILD_1")).thenReturn(Optional.of(childBranch));
        when(branchRepository.findById("BR_NEW_PARENT")).thenReturn(Optional.of(newParent));
        when(branchRepository.findByParentBranch_BranchId("BR_CHILD_1")).thenReturn(List.of(grandChild));
        when(branchRepository.save(any(FamilyBranch.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BranchDetailResponse response = genealogyService.updateBranch("BR_CHILD_1", request);

        assertNotNull(response);
        assertEquals(3, response.getGenerationLevel());
        assertEquals("BR_NEW_PARENT", response.getParentBranchId());
        // Verify grandchild generation increased to 4 (3 + delta 1)
        assertEquals(4, grandChild.getGenerationLevel());
        verify(genealogyValidator).kiemTraChuTrinh("BR_CHILD_1", "BR_NEW_PARENT");
    }

    @Test
    @DisplayName("Reject assigning parent to Root Branch -> Throws BusinessRuleException")
    void testUpdateBranch_RootCannotHaveParent_ThrowsBusinessRuleException() {
        UpdateBranchRequest request = UpdateBranchRequest.builder()
                .branchName("Rename root")
                .parentBranchId("BR_SOME_PARENT")
                .build();

        when(branchRepository.findById("BR_ROOT")).thenReturn(Optional.of(rootBranch));

        assertThrows(BusinessRuleException.class, () -> genealogyService.updateBranch("BR_ROOT", request));
    }

    @Test
    @DisplayName("Delete branch fails when branch still has child branches")
    void testDeleteBranch_HasChildren_ThrowsBusinessRuleException() {
        when(branchRepository.findById("BR_ROOT")).thenReturn(Optional.of(rootBranch));
        // Change parentBranch so it is not blocked by root rule
        rootBranch.setParentBranch(FamilyBranch.builder().branchId("BR_ANCESTOR").build());
        rootBranch.setGenerationLevel(2);

        when(branchRepository.existsByParentBranch_BranchId("BR_ROOT")).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> genealogyService.deleteBranch("BR_ROOT"));
        verify(branchRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Delete branch fails when branch still has family members")
    void testDeleteBranch_HasMembers_ThrowsBusinessRuleException() {
        when(branchRepository.findById("BR_CHILD_1")).thenReturn(Optional.of(childBranch));
        when(branchRepository.existsByParentBranch_BranchId("BR_CHILD_1")).thenReturn(false);
        when(memberRepository.existsByBranch_BranchId("BR_CHILD_1")).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> genealogyService.deleteBranch("BR_CHILD_1"));
        verify(branchRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Delete branch successfully when branch has no children and no members")
    void testDeleteBranch_Success() {
        when(branchRepository.findById("BR_CHILD_1")).thenReturn(Optional.of(childBranch));
        when(branchRepository.existsByParentBranch_BranchId("BR_CHILD_1")).thenReturn(false);
        when(memberRepository.existsByBranch_BranchId("BR_CHILD_1")).thenReturn(false);

        assertDoesNotThrow(() -> genealogyService.deleteBranch("BR_CHILD_1"));
        verify(branchRepository, times(1)).delete(childBranch);
    }

    @Test
    @DisplayName("Assign member to branch successfully when member generation >= branch generation level")
    void testAssignMemberToBranch_Success() {
        Member member = Member.builder()
                .memberId("MEM_005")
                .fullName("Nguyen Van An")
                .generation(3)
                .build();

        when(memberRepository.findById("MEM_005")).thenReturn(Optional.of(member));
        when(branchRepository.findById("BR_CHILD_1")).thenReturn(Optional.of(childBranch));

        assertDoesNotThrow(() -> genealogyService.assignMemberToBranch("MEM_005", "BR_CHILD_1"));
        assertEquals("BR_CHILD_1", member.getBranch().getBranchId());
        verify(memberRepository).save(member);
    }

    @Test
    @DisplayName("Assign member fails when member generation is smaller than branch generation level -> Throws InvalidGenerationException")
    void testAssignMemberToBranch_MemberGenerationSmallerThanBranch_ThrowsInvalidGenerationException() {
        // Member generation = 1 but Branch generation = 2 (1 < 2)
        Member member = Member.builder()
                .memberId("MEM_ANCESTOR")
                .fullName("Ancient Ancestor")
                .generation(1)
                .build();

        when(memberRepository.findById("MEM_ANCESTOR")).thenReturn(Optional.of(member));
        when(branchRepository.findById("BR_CHILD_1")).thenReturn(Optional.of(childBranch));

        assertThrows(InvalidGenerationException.class, () ->
                genealogyService.assignMemberToBranch("MEM_ANCESTOR", "BR_CHILD_1")
        );
        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("Query nested hierarchical branch tree successfully")
    void testGetBranchTree_Success() {
        when(branchRepository.findByParentBranchIsNull()).thenReturn(Optional.of(rootBranch));
        when(memberRepository.findByBranch_BranchId("BR_ROOT")).thenReturn(Collections.emptyList());
        when(branchRepository.findByParentBranch_BranchId("BR_ROOT")).thenReturn(List.of(childBranch));
        when(memberRepository.findByBranch_BranchId("BR_CHILD_1")).thenReturn(Collections.emptyList());
        when(branchRepository.findByParentBranch_BranchId("BR_CHILD_1")).thenReturn(Collections.emptyList());

        BranchTreeNodeResponse tree = genealogyService.getBranchTree();

        assertNotNull(tree);
        assertEquals("BR_ROOT", tree.getBranchId());
        assertEquals(1, tree.getChildren().size());
        assertEquals("BR_CHILD_1", tree.getChildren().get(0).getBranchId());
    }
}
