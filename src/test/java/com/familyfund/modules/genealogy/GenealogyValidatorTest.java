package com.familyfund.modules.genealogy;

import com.familyfund.core.exception.BranchCycleException;
import com.familyfund.core.exception.InvalidGenerationException;
import com.familyfund.modules.genealogy.entity.FamilyBranch;
import com.familyfund.modules.genealogy.repository.FamilyBranchRepository;
import com.familyfund.modules.genealogy.validator.GenealogyValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenealogyValidatorTest {

    @Mock
    private FamilyBranchRepository branchRepository;

    @InjectMocks
    private GenealogyValidator validator;

    @Test
    @DisplayName("Bẫy chu trình trực tiếp: Nhánh tự trỏ vào chính nó làm cha -> Tung BranchCycleException")
    void testCycle_SelfParent_ThrowsBranchCycleException() {
        assertThrows(BranchCycleException.class, () ->
                validator.kiemTraChuTrinh("BR_01", "BR_01")
        );
    }

    @Test
    @DisplayName("Bẫy chu trình trực tiếp 2 cấp: A là cha B, đổi cha của A thành B -> Tung BranchCycleException")
    void testCycle_DirectLoop_ThrowsBranchCycleException() {
        // Cấu trúc: A là cha của B
        FamilyBranch branchA = FamilyBranch.builder().branchId("BR_A").generationLevel(1).parentBranch(null).build();
        FamilyBranch branchB = FamilyBranch.builder().branchId("BR_B").generationLevel(2).parentBranch(branchA).build();

        when(branchRepository.findById("BR_B")).thenReturn(Optional.of(branchB));

        // Thử đổi cha của A thành B: validator.kiemTraChuTrinh("BR_A", "BR_B")
        assertThrows(BranchCycleException.class, () ->
                validator.kiemTraChuTrinh("BR_A", "BR_B")
        );
    }

    @Test
    @DisplayName("Bẫy chu trình gián tiếp đa cấp: A -> B -> C -> D, cố tình gán D làm cha của A -> Tung BranchCycleException")
    void testCycle_MultiLevelLoop_ThrowsBranchCycleException() {
        // Cấu trúc chuỗi: A (đời 1) -> B (đời 2) -> C (đời 3) -> D (đời 4)
        FamilyBranch branchA = FamilyBranch.builder().branchId("BR_A").generationLevel(1).parentBranch(null).build();
        FamilyBranch branchB = FamilyBranch.builder().branchId("BR_B").generationLevel(2).parentBranch(branchA).build();
        FamilyBranch branchC = FamilyBranch.builder().branchId("BR_C").generationLevel(3).parentBranch(branchB).build();
        FamilyBranch branchD = FamilyBranch.builder().branchId("BR_D").generationLevel(4).parentBranch(branchC).build();

        when(branchRepository.findById("BR_D")).thenReturn(Optional.of(branchD));
        when(branchRepository.findById("BR_C")).thenReturn(Optional.of(branchC));
        when(branchRepository.findById("BR_B")).thenReturn(Optional.of(branchB));

        // Thử gán cha của A thành D: duyệt từ D -> C -> B -> A (gặp A là currentBranchId)
        assertThrows(BranchCycleException.class, () ->
                validator.kiemTraChuTrinh("BR_A", "BR_D")
        );
    }

    @Test
    @DisplayName("Cấu trúc hợp lệ không có chu trình -> Hoàn thành không ném ngoại lệ")
    void testValidHierarchy_NoCycle() {
        FamilyBranch branchRoot = FamilyBranch.builder().branchId("BR_ROOT").generationLevel(1).parentBranch(null).build();
        FamilyBranch branchA = FamilyBranch.builder().branchId("BR_A").generationLevel(2).parentBranch(branchRoot).build();

        when(branchRepository.findById("BR_A")).thenReturn(Optional.of(branchA));
        when(branchRepository.findById("BR_ROOT")).thenReturn(Optional.of(branchRoot));

        // Tạo nhánh con mới BR_NEW dưới BR_A
        assertDoesNotThrow(() ->
                validator.kiemTraChuTrinh("BR_NEW", "BR_A")
        );
    }

    @Test
    @DisplayName("Bẫy sai lệch đời thứ: Đời con nhỏ hơn đời cha (doiThu_con < doiThu_cha) -> Tung InvalidGenerationException")
    void testGenerationValidation_ChildSmallerThanParent_ThrowsInvalidGenerationException() {
        assertThrows(InvalidGenerationException.class, () ->
                validator.kiemTraDoiThu(3, 2)
        );
    }

    @Test
    @DisplayName("Bẫy sai lệch đời thứ: Đời con bằng đời cha (doiThu_con == doiThu_cha) -> Tung InvalidGenerationException")
    void testGenerationValidation_ChildEqualsParent_ThrowsInvalidGenerationException() {
        assertThrows(InvalidGenerationException.class, () ->
                validator.kiemTraDoiThu(3, 3)
        );
    }

    @Test
    @DisplayName("Đời thứ hợp lệ: Đời con lớn hơn đời cha (doiThu_con > doiThu_cha) -> Không ném ngoại lệ")
    void testGenerationValidation_ChildGreaterThanParent_Success() {
        assertDoesNotThrow(() ->
                validator.kiemTraDoiThu(3, 4)
        );
    }
}
