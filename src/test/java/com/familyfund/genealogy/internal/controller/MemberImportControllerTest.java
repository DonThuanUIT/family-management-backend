package com.familyfund.genealogy.internal.controller;

import com.familyfund.core.exception.GlobalExceptionHandler;
import com.familyfund.genealogy.internal.dto.request.ConfirmMemberImportRequest;
import com.familyfund.genealogy.internal.dto.response.ImportMemberPreviewRow;
import com.familyfund.genealogy.internal.dto.response.ImportMembersConfirmResponse;
import com.familyfund.genealogy.internal.dto.response.ImportMembersPreviewResponse;
import com.familyfund.genealogy.internal.dto.response.MemberResponse;
import com.familyfund.genealogy.internal.service.MemberImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("US-31: MemberImportController MockMvc Tests")
class MemberImportControllerTest {

    private MockMvc mockMvc;

    @Mock
    private MemberImportService memberImportService;

    @InjectMocks
    private MemberImportController controller;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/members/import-template trả về file Excel .xlsx")
    void downloadTemplate_returnsXlsxBinary() throws Exception {
        when(memberImportService.generateTemplate()).thenReturn(new byte[]{1, 2, 3});

        mockMvc.perform(get("/api/v1/members/import-template"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("mau-nhap-thanh-vien.xlsx")));
    }

    @Test
    @DisplayName("POST /api/members/import-preview trả về kết quả kiểm tra từng dòng")
    void previewImport_returnsPreviewResult() throws Exception {
        ImportMembersPreviewResponse response = ImportMembersPreviewResponse.builder()
                .fileName("members.csv")
                .totalRows(1)
                .validCount(0)
                .errorCount(1)
                .rows(List.of(ImportMemberPreviewRow.builder()
                        .rowNumber(2)
                        .valid(false)
                        .errors(List.of("Thiếu mã nhánh (branch_id)"))
                        .fullName("Nguyễn Văn A")
                        .build()))
                .build();

        when(memberImportService.previewImport(any())).thenReturn(response);

        MockMultipartFile file = new MockMultipartFile("file", "members.csv", "text/csv", "data".getBytes());

        mockMvc.perform(multipart("/api/members/import-preview").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalRows").value(1))
                .andExpect(jsonPath("$.data.errorCount").value(1))
                .andExpect(jsonPath("$.data.rows[0].valid").value(false))
                .andExpect(jsonPath("$.data.rows[0].errors[0]").value("Thiếu mã nhánh (branch_id)"));
    }

    @Test
    @DisplayName("POST /api/members/import-confirm trả về 200 khi hợp lệ và 400 khi danh sách rỗng")
    void confirmImport_validAndEmptyPayload() throws Exception {
        when(memberImportService.confirmImport(any(ConfirmMemberImportRequest.class)))
                .thenReturn(ImportMembersConfirmResponse.builder()
                        .insertedCount(1)
                        .skippedCount(0)
                        .insertedMembers(List.of(MemberResponse.builder()
                                .memberId("MEM_01")
                                .fullName("Nguyễn Văn A")
                                .build()))
                        .skippedRows(List.of())
                        .build());

        String validJson = """
                {
                  "rows": [
                    {
                      "rowNumber": 2,
                      "fullName": "Nguyễn Văn A",
                      "gender": "Male",
                      "generation": 2,
                      "branchId": "BR_01"
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/members/import-confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.insertedCount").value(1));

        mockMvc.perform(post("/api/members/import-confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rows\": []}"))
                .andExpect(status().isBadRequest());
    }
}
