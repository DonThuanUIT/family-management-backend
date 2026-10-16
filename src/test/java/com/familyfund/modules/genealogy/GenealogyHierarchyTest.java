package com.familyfund.modules.genealogy;

import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.core.exception.InvalidGenerationException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.modules.genealogy.dto.BranchDetailResponse;
import com.familyfund.modules.genealogy.dto.BranchTreeNodeResponse;
import com.familyfund.modules.genealogy.dto.CreateChildBranchRequest;
import com.familyfund.modules.genealogy.dto.UpdateBranchRequest;
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
                .clanName("Nguyễn Tộc")
                .branchName("Nhánh Đại Tôn")
                .generationLevel(1)
                .parentBranch(null)
                .build();

        childBranch = FamilyBranch.builder()
                .branchId("BR_CHILD_1")
                .clanName("Nguyễn Tộc")
                .branchName("Chi Giáp")
                .generationLevel(2)
                .parentBranch(rootBranch)
                .build();
    }

    @Test
    @DisplayName("Tạo nhánh con thành công: Đời thứ tự động gán bằng đời cha + 1")
    void testCreateChildBranch_Success() {
        CreateChildBranchRequest request = CreateChildBranchRequest.builder()
                .branchName("Chi Giáp")
                .parentBranchId("BR_ROOT")
                .description("Chi giáp định cư tại Hà Nội")
                .build();

        when(branchRepository.findById("BR_ROOT")).thenReturn(Optional.of(rootBranch));
        when(branchRepository.save(any(FamilyBranch.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BranchDetailResponse response = genealogyService.createChildBranch(request);

        assertNotNull(response);
        assertEquals("Chi Giáp", response.getBranchName());
        assertEquals(2, response.getGenerationLevel());
        assertEquals("BR_ROOT", response.getParentBranchId());
        verify(genealogyValidator).kiemTraChuTrinh(null, "BR_ROOT");
        verify(genealogyValidator).kiemTraDoiThu(1, 2);
    }

    @Test
    @DisplayName("Tạo nhánh con thất bại khi không tìm thấy nhánh cha")
    void testCreateChildBranch_ParentNotFound_ThrowsResourceNotFoundException() {
        CreateChildBranchRequest request = CreateChildBranchRequest.builder()
                .branchName("Chi Giáp")
                .parentBranchId("BR_NON_EXIST")
                .build();

        when(branchRepository.findById("BR_NON_EXIST")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> genealogyService.createChildBranch(request));
        verify(branchRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cập nhật chuyển nhánh cha và kích hoạt cập nhật đời thứ đệ quy (Cascade)")
    void testUpdateBranch_Reparenting_Success_WithCascade() {
        // Nhánh cha mới: BR_NEW_PARENT có generation = 2 -> childBranch sẽ chuyển thành generation = 3 (delta = +1)
        FamilyBranch newParent = FamilyBranch.builder()
                .branchId("BR_NEW_PARENT")
                .generationLevel(2)
                .build();

        // Nhánh cháu: BR_GRANDCHILD trực thuộc childBranch có generation = 3
        FamilyBranch grandChild = FamilyBranch.builder()
                .branchId("BR_GRANDCHILD")
                .generationLevel(3)
                .parentBranch(childBranch)
                .build();

        UpdateBranchRequest request = UpdateBranchRequest.builder()
                .branchName("Chi Giáp Đổi Tên")
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
        // Verify nhánh cháu được tăng lên đời 4 (3 + delta 1)
        assertEquals(4, grandChild.getGenerationLevel());
        verify(genealogyValidator).kiemTraChuTrinh("BR_CHILD_1", "BR_NEW_PARENT");
    }

    @Test
    @DisplayName("Chặn gán nhánh cha cho Nhánh Gốc -> Tung BusinessRuleException")
    void testUpdateBranch_RootCannotHaveParent_ThrowsBusinessRuleException() {
        UpdateBranchRequest request = UpdateBranchRequest.builder()
                .branchName("Đổi tên gốc")
                .parentBranchId("BR_SOME_PARENT")
                .build();

        when(branchRepository.findById("BR_ROOT")).thenReturn(Optional.of(rootBranch));

        assertThrows(BusinessRuleException.class, () -> genealogyService.updateBranch("BR_ROOT", request));
    }

    @Test
    @DisplayName("Xóa nhánh thất bại khi nhánh vẫn còn nhánh con trực thuộc")
    void testDeleteBranch_HasChildren_ThrowsBusinessRuleException() {
        when(branchRepository.findById("BR_ROOT")).thenReturn(Optional.of(rootBranch));
        // Đổi parentBranch để không bị chặn bởi luật root
        rootBranch.setParentBranch(FamilyBranch.builder().branchId("BR_ANCESTOR").build());
        rootBranch.setGenerationLevel(2);

        when(branchRepository.existsByParentBranch_BranchId("BR_ROOT")).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> genealogyService.deleteBranch("BR_ROOT"));
        verify(branchRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Xóa nhánh thất bại khi nhánh vẫn còn thành viên gia đình")
    void testDeleteBranch_HasMembers_ThrowsBusinessRuleException() {
        when(branchRepository.findById("BR_CHILD_1")).thenReturn(Optional.of(childBranch));
        when(branchRepository.existsByParentBranch_BranchId("BR_CHILD_1")).thenReturn(false);
        when(memberRepository.existsByBranch_BranchId("BR_CHILD_1")).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> genealogyService.deleteBranch("BR_CHILD_1"));
        verify(branchRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Xóa nhánh thành công khi nhánh không có con và không có thành viên")
    void testDeleteBranch_Success() {
        when(branchRepository.findById("BR_CHILD_1")).thenReturn(Optional.of(childBranch));
        when(branchRepository.existsByParentBranch_BranchId("BR_CHILD_1")).thenReturn(false);
        when(memberRepository.existsByBranch_BranchId("BR_CHILD_1")).thenReturn(false);

        assertDoesNotThrow(() -> genealogyService.deleteBranch("BR_CHILD_1"));
        verify(branchRepository, times(1)).delete(childBranch);
    }

    @Test
    @DisplayName("Gán thành viên vào nhánh thành công khi đời thứ thành viên >= đời thứ nhánh")
    void testAssignMemberToBranch_Success() {
        Member member = Member.builder()
                .memberId("MEM_005")
                .fullName("Nguyễn Văn An")
                .generation(3)
                .build();

        when(memberRepository.findById("MEM_005")).thenReturn(Optional.of(member));
        when(branchRepository.findById("BR_CHILD_1")).thenReturn(Optional.of(childBranch));

        assertDoesNotThrow(() -> genealogyService.assignMemberToBranch("MEM_005", "BR_CHILD_1"));
        assertEquals("BR_CHILD_1", member.getBranch().getBranchId());
        verify(memberRepository).save(member);
    }

    @Test
    @DisplayName("Gán thành viên thất bại khi đời thành viên nhỏ hơn đời nhánh -> Tung InvalidGenerationException")
    void testAssignMemberToBranch_MemberGenerationSmallerThanBranch_ThrowsInvalidGenerationException() {
        // Member generation = 1 nhưng Branch generation = 2 (1 < 2)
        Member member = Member.builder()
                .memberId("MEM_ANCESTOR")
                .fullName("Cụ Tiên Tổ")
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
    @DisplayName("Truy vấn cây phả hệ phân cấp lồng nhau thành công")
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
