package com.familyfund.modules.genealogy;

import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.modules.genealogy.dto.BranchDetailResponse;
import com.familyfund.modules.genealogy.dto.UpdateRootBranchRequest;
import com.familyfund.modules.genealogy.entity.FamilyBranch;
import com.familyfund.modules.genealogy.entity.Member;
import com.familyfund.modules.genealogy.repository.FamilyBranchRepository;
import com.familyfund.modules.genealogy.repository.MemberRepository;
import com.familyfund.modules.genealogy.service.impl.GenealogyServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GenealogyRootBranchTest {

    @Mock
    private FamilyBranchRepository branchRepository;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private GenealogyServiceImpl genealogyService;

    private FamilyBranch mockRootBranch;

    @BeforeEach
    void setUp() {
        mockRootBranch = FamilyBranch.builder()
                .branchId("BR_ROOT")
                .clanName("Dòng Họ Mặc Định")
                .branchName("Nhánh Gốc Mặc Định")
                .generationLevel(1)
                .parentBranch(null)
                .build();
    }

    @Test
    @DisplayName("Cập nhật thành công thông tin nhánh gốc khi đã tồn tại nhánh gốc")
    void testUpdateRootBranch_Success_WhenRootExists() {
        UpdateRootBranchRequest request = UpdateRootBranchRequest.builder()
                .clanName("Nguyễn Tộc")
                .branchName("Nhánh Đại Tôn")
                .description("Nhánh khởi tổ làng Cổ Am")
                .build();

        when(branchRepository.findByParentBranchIsNull()).thenReturn(Optional.of(mockRootBranch));
        when(branchRepository.save(any(FamilyBranch.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BranchDetailResponse response = genealogyService.updateRootBranch(request);

        assertNotNull(response);
        assertEquals("Nguyễn Tộc", response.getClanName());
        assertEquals("Nhánh Đại Tôn", response.getBranchName());
        assertEquals(1, response.getGenerationLevel());
        assertNull(response.getParentBranchId());
        verify(branchRepository, times(1)).save(mockRootBranch);
    }

    @Test
    @DisplayName("Cập nhật thành công thông tin nhánh gốc kèm người đại diện/thủy tổ")
    void testUpdateRootBranch_Success_WithRepresentative() {
        Member mockRep = Member.builder()
                .memberId("MEM_001")
                .fullName("Cụ Thủy Tổ Nguyễn Quý Công")
                .generation(1)
                .gender("Male")
                .build();

        UpdateRootBranchRequest request = UpdateRootBranchRequest.builder()
                .clanName("Nguyễn Tộc")
                .branchName("Nhánh Đại Tôn")
                .representativeId("MEM_001")
                .build();

        when(branchRepository.findByParentBranchIsNull()).thenReturn(Optional.of(mockRootBranch));
        when(memberRepository.findById("MEM_001")).thenReturn(Optional.of(mockRep));
        when(branchRepository.save(any(FamilyBranch.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BranchDetailResponse response = genealogyService.updateRootBranch(request);

        assertNotNull(response);
        assertEquals("MEM_001", response.getRepresentativeId());
        assertEquals("Cụ Thủy Tổ Nguyễn Quý Công", response.getRepresentativeName());
        verify(memberRepository, times(1)).findById("MEM_001");
    }

    @Test
    @DisplayName("Quăng ngoại lệ ResourceNotFoundException khi không tìm thấy mã người đại diện")
    void testUpdateRootBranch_RepresentativeNotFound_ThrowsException() {
        UpdateRootBranchRequest request = UpdateRootBranchRequest.builder()
                .clanName("Nguyễn Tộc")
                .branchName("Nhánh Đại Tôn")
                .representativeId("MEM_NON_EXIST")
                .build();

        when(branchRepository.findByParentBranchIsNull()).thenReturn(Optional.of(mockRootBranch));
        when(memberRepository.findById("MEM_NON_EXIST")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> genealogyService.updateRootBranch(request));
        verify(branchRepository, never()).save(any());
    }

    @Test
    @DisplayName("Lấy thông tin nhánh gốc thành công")
    void testGetRootBranch_Success() {
        when(branchRepository.findByParentBranchIsNull()).thenReturn(Optional.of(mockRootBranch));

        BranchDetailResponse response = genealogyService.getRootBranch();

        assertNotNull(response);
        assertEquals("BR_ROOT", response.getBranchId());
        assertEquals(1, response.getGenerationLevel());
    }

    @Test
    @DisplayName("Lấy thông tin nhánh gốc quăng lỗi nếu chưa khởi tạo nhánh gốc")
    void testGetRootBranch_NotFound_ThrowsException() {
        when(branchRepository.findByParentBranchIsNull()).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> genealogyService.getRootBranch());
    }
}
