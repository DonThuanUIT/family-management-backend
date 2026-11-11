package com.familyfund.genealogy.internal.controller;

import com.familyfund.core.exception.BadRequestException;
import com.familyfund.core.exception.GlobalExceptionHandler;
import com.familyfund.genealogy.internal.dto.request.UpdateLifeStatusRequest;
import com.familyfund.genealogy.internal.dto.response.MemberResponse;
import com.familyfund.genealogy.internal.service.GenealogyService;
import com.familyfund.genealogy.internal.service.MemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("US-27: MemberController Life Status Controller Tests")
class MemberControllerLifeStatusTest {

    private MockMvc mockMvc;

    @Mock
    private GenealogyService genealogyService;

    @Mock
    private MemberService memberService;

    @InjectMocks
    private MemberController memberController;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(memberController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("PUT /api/v1/members/{memberId}/life-status returns 200 OK when marking Deceased")
    void updateLifeStatus_deceased_success() throws Exception {
        String memberId = "MEM_001";
        MemberResponse response = MemberResponse.builder()
                .memberId(memberId)
                .fullName("Nguyễn Văn An")
                .status("Deceased")
                .deathDate(LocalDate.of(2024, 3, 24))
                .deathDateLunar("15/02 Giáp Thìn")
                .burialPlace("Khu lăng mộ họ Nguyễn")
                .branchId("BR_01")
                .branchName("Chi 1 (Đông Kết)")
                .clanId("CLAN_01")
                .build();

        when(memberService.updateLifeStatus(eq(memberId), any(UpdateLifeStatusRequest.class)))
                .thenReturn(response);

        String jsonRequest = """
                {
                    "status": "Deceased",
                    "deathDate": "2024-03-24",
                    "burialPlace": "Khu lăng mộ họ Nguyễn"
                }
                """;

        mockMvc.perform(put("/api/v1/members/{memberId}/life-status", memberId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Cập nhật tình trạng thành viên thành công"))
                .andExpect(jsonPath("$.data.memberId").value(memberId))
                .andExpect(jsonPath("$.data.status").value("Deceased"))
                .andExpect(jsonPath("$.data.deathDate").value("2024-03-24"))
                .andExpect(jsonPath("$.data.deathDateLunar").value("15/02 Giáp Thìn"))
                .andExpect(jsonPath("$.data.burialPlace").value("Khu lăng mộ họ Nguyễn"));

        verify(memberService).updateLifeStatus(eq(memberId), any(UpdateLifeStatusRequest.class));
    }

    @Test
    @DisplayName("PUT /api/v1/members/{memberId}/life-status returns 200 OK when reverting to Living")
    void updateLifeStatus_living_success() throws Exception {
        String memberId = "MEM_001";
        MemberResponse response = MemberResponse.builder()
                .memberId(memberId)
                .fullName("Nguyễn Văn An")
                .status("Living")
                .branchId("BR_01")
                .branchName("Chi 1 (Đông Kết)")
                .clanId("CLAN_01")
                .build();

        when(memberService.updateLifeStatus(eq(memberId), any(UpdateLifeStatusRequest.class)))
                .thenReturn(response);

        String jsonRequest = """
                {
                    "status": "Living",
                    "deathDate": null,
                    "burialPlace": null
                }
                """;

        mockMvc.perform(put("/api/v1/members/{memberId}/life-status", memberId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.memberId").value(memberId))
                .andExpect(jsonPath("$.data.status").value("Living"))
                .andExpect(jsonPath("$.data.deathDate").doesNotExist())
                .andExpect(jsonPath("$.data.deathDateLunar").doesNotExist());
    }

    @Test
    @DisplayName("PUT /api/v1/members/{memberId}/life-status returns 400 when status is Deceased but deathDate is missing")
    void updateLifeStatus_missingDeathDate_returnsBadRequest() throws Exception {
        String memberId = "MEM_001";

        String jsonRequest = """
                {
                    "status": "Deceased",
                    "deathDate": null
                }
                """;

        mockMvc.perform(put("/api/v1/members/{memberId}/life-status", memberId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("PUT /api/v1/members/{memberId}/life-status returns 400 when status is invalid")
    void updateLifeStatus_invalidStatus_returnsBadRequest() throws Exception {
        String memberId = "MEM_001";

        String jsonRequest = """
                {
                    "status": "InvalidStatus",
                    "deathDate": "2024-03-24"
                }
                """;

        mockMvc.perform(put("/api/v1/members/{memberId}/life-status", memberId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
