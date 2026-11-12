package com.familyfund.genealogy.internal.service;

import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.core.security.SystemRoles;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.genealogy.internal.dto.request.ConfirmMemberImportRequest;
import com.familyfund.genealogy.internal.dto.request.ImportMemberRowRequest;
import com.familyfund.genealogy.internal.dto.response.ImportMembersConfirmResponse;
import com.familyfund.genealogy.internal.dto.response.ImportMembersPreviewResponse;
import com.familyfund.genealogy.internal.dto.response.MemberResponse;
import com.familyfund.genealogy.internal.entity.Clan;
import com.familyfund.genealogy.internal.entity.FamilyBranch;
import com.familyfund.genealogy.internal.entity.Member;
import com.familyfund.genealogy.internal.mapper.MemberMapper;
import com.familyfund.genealogy.internal.repository.ClanRepository;
import com.familyfund.genealogy.internal.repository.FamilyBranchRepository;
import com.familyfund.genealogy.internal.repository.MemberRepository;
import com.familyfund.genealogy.internal.security.MemberPrivacyPolicy;
import com.familyfund.genealogy.internal.util.MemberIdGenerator;
import com.familyfund.genealogy.internal.util.VietnameseLunarCalendarConverter;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("US-31: MemberImportServiceImpl Unit Tests")
class MemberImportServiceImplTest {

    private static final String CLAN_ID = "CLAN_01";

    @Mock
    private ClanRepository clanRepository;
    @Mock
    private FamilyBranchRepository branchRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private MemberIdGenerator memberIdGenerator;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private GenealogyQueryService genealogyQueryService;
    @Mock
    private MemberMapper memberMapper;
    @Mock
    private MemberPrivacyPolicy memberPrivacyPolicy;

    private MemberImportServiceImpl service;

    private FamilyBranch branch1;
    private FamilyBranch branch2;

    @BeforeEach
    void setUp() {
        TenantContext.setClanId(CLAN_ID);
        service = new MemberImportServiceImpl(
                new MemberSpreadsheetParser(),
                new MemberImportTemplateGenerator(),
                clanRepository,
                branchRepository,
                memberRepository,
                memberIdGenerator,
                currentUserProvider,
                genealogyQueryService,
                new VietnameseLunarCalendarConverter(),
                memberMapper,
                memberPrivacyPolicy
        );

        branch1 = FamilyBranch.builder()
                .branchId("BR_01")
                .clanId(CLAN_ID)
                .branchName("Chi 1")
                .generationLevel(2)
                .build();
        branch2 = FamilyBranch.builder()
                .branchId("BR_02")
                .clanId(CLAN_ID)
                .branchName("Chi 2")
                .generationLevel(3)
                .build();

        lenient().when(branchRepository.findByClanIdOrderByGenerationLevelAsc(CLAN_ID))
                .thenReturn(List.of(branch1, branch2));
        lenient().when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Tạo file mẫu Excel (.xlsx) gồm sheet Members và HuongDan")
    void generateTemplate_returnsValidXlsxWorkbook() throws Exception {
        byte[] bytes = service.generateTemplate();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(wb.getSheet("Members")).isNotNull();
            assertThat(wb.getSheet("HuongDan")).isNotNull();
            assertThat(wb.getSheet("Members").getRow(0).getCell(1).getStringCellValue()).isEqualTo("full_name");
        }
    }

    @Test
    @DisplayName("previewImport báo lỗi từng dòng: trùng mã, sai ngày, thiếu nhánh, đời nhỏ hơn nhánh, tính giỗ âm lịch")
    void previewImport_detectsRowErrorsAndComputesLunarDate() {
        when(memberRepository.findExistingMemberIds(anyCollection())).thenReturn(List.of("MEM_DUP_DB"));
        when(memberRepository.findByBranch_ClanId(CLAN_ID)).thenReturn(List.of(
                Member.builder()
                        .memberId("MEM_DUP_DB")
                        .fullName("Thành Viên Cũ")
                        .gender("Male")
                        .generation(2)
                        .branch(branch1)
                        .build()
        ));

        String csv = """
                member_id,full_name,date_of_birth,gender,generation,branch_id,status,death_date,burial_place
                ,Nguyễn Văn Hợp Lệ,1950-01-10,Male,2,BR_01,Deceased,2024-03-24,Quê nhà
                MEM_DUP_DB,Trùng Mã DB,1990-05-15,Male,3,BR_01,Living,,
                MEM_FILE_1,Sai Ngày Và Thiếu Nhánh,31/02/1990,Female,3,,Living,,
                MEM_FILE_1,Trùng Mã Trong File Và Đời Thấp,1992-05-15,Male,1,BR_02,Living,,
                """;
        MockMultipartFile file = new MockMultipartFile(
                "file", "import.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        ImportMembersPreviewResponse response = service.previewImport(file);

        assertThat(response.totalRows()).isEqualTo(4);
        assertThat(response.validCount()).isEqualTo(1);
        assertThat(response.errorCount()).isEqualTo(3);

        // Row 1 valid + lunar death date calculated
        assertThat(response.rows().get(0).valid()).isTrue();
        assertThat(response.rows().get(0).deathDateLunar()).isEqualTo("15/02 Giáp Thìn");

        // Row 2 duplicate member_id in DB
        assertThat(response.rows().get(1).valid()).isFalse();
        assertThat(response.rows().get(1).errors()).anyMatch(e -> e.contains("đã tồn tại trong hệ thống"));

        // Row 3 invalid date + missing branch_id
        assertThat(response.rows().get(2).valid()).isFalse();
        assertThat(response.rows().get(2).errors())
                .anyMatch(e -> e.contains("Sai định dạng ngày sinh"))
                .anyMatch(e -> e.contains("Thiếu mã nhánh"));

        // Row 4 duplicate member_id in file + generation < branch generationLevel
        assertThat(response.rows().get(3).valid()).isFalse();
        assertThat(response.rows().get(3).errors())
                .anyMatch(e -> e.contains("trùng lặp với dòng khác trong file"))
                .anyMatch(e -> e.contains("nhỏ hơn đời gốc"));
    }

    @Test
    @DisplayName("previewImport chặn BRANCH_HEAD nhập thành viên vào nhánh không quản lý")
    void previewImport_branchHeadOutOfScope_marksRowError() {
        when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(false);
        when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
        when(currentUserProvider.hasAuthority(SystemRoles.BRANCH_HEAD)).thenReturn(true);
        when(currentUserProvider.currentMemberId()).thenReturn(Optional.of("MEM_HEAD_1"));
        when(genealogyQueryService.getBranchIdsManagedByMember(CLAN_ID, "MEM_HEAD_1")).thenReturn(List.of("BR_01"));
        when(memberRepository.findByBranch_ClanId(CLAN_ID)).thenReturn(List.of());

        String csv = """
                full_name,gender,generation,branch_id
                Nguyễn Văn A,Male,3,BR_02
                """;
        MockMultipartFile file = new MockMultipartFile(
                "file", "import.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        ImportMembersPreviewResponse response = service.previewImport(file);
        assertThat(response.validCount()).isEqualTo(0);
        assertThat(response.errorCount()).isEqualTo(1);
        assertThat(response.rows().get(0).errors()).anyMatch(e -> e.contains("không có quyền nhập thành viên vào nhánh"));
    }

    @Test
    @DisplayName("confirmImport chỉ lưu các dòng hợp lệ với isPlaceholder=true và bỏ qua dòng lỗi")
    @SuppressWarnings("unchecked")
    void confirmImport_savesOnlyValidRows() {
        when(clanRepository.findByIdForUpdate(CLAN_ID)).thenReturn(Optional.of(Clan.builder().clanId(CLAN_ID).build()));
        when(memberRepository.findByBranch_ClanId(CLAN_ID)).thenReturn(List.of());
        when(memberIdGenerator.generate()).thenReturn("MEM_GEN_01");
        when(memberRepository.existsByMemberId("MEM_GEN_01")).thenReturn(false);
        when(memberRepository.saveAllAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(memberMapper.toResponse(any(Member.class))).thenAnswer(inv -> {
            Member m = inv.getArgument(0);
            return MemberResponse.builder()
                    .memberId(m.getMemberId())
                    .fullName(m.getFullName())
                    .branchId(m.getBranch().getBranchId())
                    .generation(m.getGeneration())
                    .status(m.getStatus())
                    .deathDateLunar(m.getDeathDateLunar())
                    .build();
        });
        when(memberPrivacyPolicy.applyPrivacy(any(), any(), any())).thenAnswer(inv -> inv.getArgument(0));

        ConfirmMemberImportRequest request = ConfirmMemberImportRequest.builder()
                .rows(List.of(
                        ImportMemberRowRequest.builder()
                                .rowNumber(2)
                                .fullName("Nguyễn Văn Hợp Lệ")
                                .dateOfBirth(LocalDate.of(1990, 5, 15))
                                .gender("Male")
                                .generation(3)
                                .branchId("BR_01")
                                .status("Living")
                                .build(),
                        ImportMemberRowRequest.builder()
                                .rowNumber(3)
                                .fullName("Dòng Thiếu Nhánh")
                                .gender("Female")
                                .generation(3)
                                .branchId(null)
                                .build()
                ))
                .build();

        ImportMembersConfirmResponse response = service.confirmImport(request);

        assertThat(response.insertedCount()).isEqualTo(1);
        assertThat(response.skippedCount()).isEqualTo(1);
        assertThat(response.insertedMembers().get(0).memberId()).isEqualTo("MEM_GEN_01");
        assertThat(response.skippedRows().get(0).rowNumber()).isEqualTo(3);

        ArgumentCaptor<List<Member>> savedCaptor = ArgumentCaptor.forClass(List.class);
        verify(memberRepository).saveAllAndFlush(savedCaptor.capture());
        assertThat(savedCaptor.getValue()).hasSize(1);
        assertThat(savedCaptor.getValue().get(0).getIsPlaceholder()).isTrue();
    }
}
