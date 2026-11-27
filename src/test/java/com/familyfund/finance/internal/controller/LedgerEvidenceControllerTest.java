package com.familyfund.finance.internal.controller;

import com.familyfund.core.exception.GlobalExceptionHandler;
import com.familyfund.core.storage.MediaException;
import com.familyfund.finance.internal.dto.response.LedgerEvidenceUploadResponse;
import com.familyfund.finance.internal.service.LedgerEvidenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class LedgerEvidenceControllerTest {

    private MockMvc mockMvc;

    @Mock
    private LedgerEvidenceService ledgerEvidenceService;

    @InjectMocks
    private LedgerEvidenceController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/ledger/evidence-upload trả về 201 và metadata tệp đã lưu")
    void uploadEvidenceReturns201() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "Bien_ban_2026.pdf",
                "application/pdf",
                "%PDF-1.4\n%%EOF".getBytes(StandardCharsets.US_ASCII)
        );
        LedgerEvidenceUploadResponse res = new LedgerEvidenceUploadResponse(
                "/api/v1/ledger/evidence/550e8400-e29b-41d4-a716-446655440000.pdf",
                "550e8400-e29b-41d4-a716-446655440000.pdf",
                "Bien_ban_2026.pdf",
                "application/pdf",
                14L,
                "a".repeat(64)
        );
        when(ledgerEvidenceService.uploadEvidence(any(), eq("LDG001"))).thenReturn(res);

        mockMvc.perform(multipart("/api/v1/ledger/evidence-upload")
                        .file(file)
                        .param("originalTransactionId", "LDG001"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.evidenceUrl").value(res.evidenceUrl()))
                .andExpect(jsonPath("$.data.fileName").value(res.fileName()));

        verify(ledgerEvidenceService).uploadEvidence(any(), eq("LDG001"));
    }

    @Test
    @DisplayName("POST /api/v1/ledger/evidence-upload trả về 415 khi service ném MediaException")
    void uploadEvidencePropagatesMediaException() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "bad.txt", "text/plain", "bad".getBytes(StandardCharsets.UTF_8));
        when(ledgerEvidenceService.uploadEvidence(any(), any()))
                .thenThrow(new MediaException(415, "UNSUPPORTED_MEDIA_TYPE", "Chỉ hỗ trợ PDF, JPG hoặc PNG"));

        mockMvc.perform(multipart("/api/v1/ledger/evidence-upload").file(file))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    @DisplayName("GET /api/v1/ledger/evidence/{fileName} trả về stream kèm header bảo mật")
    void downloadEvidenceReturnsStreamWithSecurityHeaders() throws Exception {
        String fileName = "550e8400-e29b-41d4-a716-446655440000.pdf";
        byte[] payload = "pdf-bytes".getBytes(StandardCharsets.US_ASCII);
        when(ledgerEvidenceService.downloadEvidence(fileName))
                .thenReturn(new LedgerEvidenceService.EvidenceDownload(
                        new ByteArrayInputStream(payload), fileName, "application/pdf"));

        mockMvc.perform(get("/api/v1/ledger/evidence/{fileName}", fileName)
                        .param("disposition", "attachment"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(content().contentType("application/pdf"))
                .andExpect(content().bytes(payload));
    }
}
