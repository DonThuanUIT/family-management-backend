package com.familyfund.finance.internal.service;

import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.core.security.SystemRoles;
import com.familyfund.core.storage.FileStorage;
import com.familyfund.core.storage.MediaException;
import com.familyfund.core.storage.StorageProperties;
import com.familyfund.core.storage.StorageRegistry;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.event.api.EventQueryService;
import com.familyfund.event.api.dto.EventRole;
import com.familyfund.finance.api.dto.LedgerTransactionType;
import com.familyfund.finance.internal.dto.response.LedgerEvidenceUploadResponse;
import com.familyfund.finance.internal.entity.ImmutableLedger;
import com.familyfund.finance.internal.repository.ImmutableLedgerRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LedgerEvidenceServiceImplTest {

    @Mock
    private StorageRegistry storageRegistry;

    @Mock
    private FileStorage fileStorage;

    @Spy
    private StorageProperties storageProperties = new StorageProperties();

    @Mock
    private ImmutableLedgerRepository immutableLedgerRepository;

    @Mock
    private EventQueryService eventQueryService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private LedgerEvidenceServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.setClanId("CLAN_01");
        lenient().when(storageRegistry.current()).thenReturn(fileStorage);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private byte[] createValidPdfBytes() throws IOException {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage());
            doc.save(out);
            return out.toByteArray();
        }
    }

    private byte[] createValidPngBytes() throws IOException {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, Color.RED.getRGB());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private ImmutableLedger sampleLedger(String eventId) {
        return ImmutableLedger.builder()
                .transactionId("LDG001")
                .clanId("CLAN_01")
                .eventId(eventId)
                .transactionType(LedgerTransactionType.SurplusTransfer)
                .amount(new BigDecimal("1000000.00"))
                .runningBalance(new BigDecimal("1000000.00"))
                .description("Chuyển thặng dư sự kiện")
                .createdByAccountId("ACC_1")
                .approvedByAccountId("ACC_HEAD")
                .previousHash("GENESIS")
                .recordHash("a".repeat(64))
                .createdAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("uploadEvidence: PDF hợp lệ từ Trưởng tộc lưu lên storage đúng key và trả proxy URL")
    void uploadPdfSuccess() throws IOException {
        when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
        when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(true);

        byte[] pdfBytes = createValidPdfBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "Bien_ban_doi_soat.pdf", "application/pdf", pdfBytes);

        LedgerEvidenceUploadResponse response = service.uploadEvidence(file, null);

        assertThat(response.evidenceUrl()).startsWith("/api/v1/ledger/evidence/");
        assertThat(response.evidenceUrl()).endsWith(".pdf");
        assertThat(response.fileName()).endsWith(".pdf");
        assertThat(response.originalFilename()).isEqualTo("Bien_ban_doi_soat.pdf");
        assertThat(response.mimeType()).isEqualTo("application/pdf");
        assertThat(response.sizeBytes()).isEqualTo(pdfBytes.length);
        assertThat(response.sha256Checksum()).hasSize(64);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(fileStorage).put(eq("member-media"), keyCaptor.capture(), eq(pdfBytes), eq("application/pdf"));
        assertThat(keyCaptor.getValue()).startsWith("CLAN_01/ledger-evidence/");
        assertThat(keyCaptor.getValue()).endsWith(".pdf");
    }

    @Test
    @DisplayName("uploadEvidence: Ảnh PNG hợp lệ được re-encode sạch metadata và lưu trữ")
    void uploadPngSuccess() throws IOException {
        when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(true);

        byte[] pngBytes = createValidPngBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "hoa_don.png", "image/png", pngBytes);

        LedgerEvidenceUploadResponse response = service.uploadEvidence(file, null);

        assertThat(response.mimeType()).isEqualTo("image/png");
        assertThat(response.fileName()).endsWith(".png");
        verify(fileStorage).put(eq("member-media"), any(), any(), eq("image/png"));
    }

    @Test
    @DisplayName("uploadEvidence: Từ chối file định dạng không hỗ trợ (.txt) với HTTP 415")
    void rejectUnsupportedFileType() {
        when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
        when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(true);

        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.uploadEvidence(file, null))
                .isInstanceOf(MediaException.class)
                .satisfies(ex -> assertThat(((MediaException) ex).getStatus()).isEqualTo(415));

        verify(fileStorage, never()).put(any(), any(), any(), any());
    }

    @Test
    @DisplayName("uploadEvidence: Từ chối file PDF giả mạo sai magic bytes")
    void rejectFakePdf() {
        when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
        when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(true);

        MockMultipartFile file = new MockMultipartFile(
                "file", "fake.pdf", "application/pdf", "not-a-real-pdf".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.uploadEvidence(file, null))
                .isInstanceOf(MediaException.class)
                .satisfies(ex -> assertThat(((MediaException) ex).getStatus()).isEqualTo(400));

        verify(fileStorage, never()).put(any(), any(), any(), any());
    }

    @Test
    @DisplayName("uploadEvidence: Từ chối file vượt 10 MB với HTTP 413")
    void rejectOversizedFile() {
        when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
        when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(true);

        byte[] tooBig = new byte[(10 * 1024 * 1024) + 1];
        MockMultipartFile file = new MockMultipartFile(
                "file", "huge.pdf", "application/pdf", tooBig);

        assertThatThrownBy(() -> service.uploadEvidence(file, null))
                .isInstanceOf(MediaException.class)
                .satisfies(ex -> assertThat(((MediaException) ex).getStatus()).isEqualTo(413));
    }

    @Test
    @DisplayName("uploadEvidence: Thủ quỹ sự kiện hợp lệ được phép upload khi khớp eventId, Member thường bị chặn 403")
    void eventTreasurerAuthorizedAndRegularMemberDenied() throws IOException {
        when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
        when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(false);
        when(immutableLedgerRepository.findByTransactionIdAndClanId("LDG001", "CLAN_01"))
                .thenReturn(Optional.of(sampleLedger("EVT_01")));
        when(currentUserProvider.currentMemberId()).thenReturn(Optional.of("MEM_TREASURER"));
        when(eventQueryService.getMemberRolesInEvent("EVT_01", "MEM_TREASURER"))
                .thenReturn(Set.of(EventRole.EventTreasurer));

        MockMultipartFile file = new MockMultipartFile(
                "file", "bien_ban.pdf", "application/pdf", createValidPdfBytes());

        LedgerEvidenceUploadResponse response = service.uploadEvidence(file, "LDG001");
        assertThat(response.evidenceUrl()).contains("/api/v1/ledger/evidence/");

        when(currentUserProvider.currentMemberId()).thenReturn(Optional.of("MEM_NORMAL"));
        when(eventQueryService.getMemberRolesInEvent("EVT_01", "MEM_NORMAL"))
                .thenReturn(Set.of());

        assertThatThrownBy(() -> service.uploadEvidence(file, "LDG001"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("uploadEvidence: Trưởng chi và Thủ quỹ sự kiện được phép upload chứng từ chi Quỹ chung (không gắn txId), Member bị chặn 403")
    void branchHeadAndEventTreasurerAllowedWithoutTxId() throws IOException {
        when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
        when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(false);
        when(currentUserProvider.hasAuthority(SystemRoles.BRANCH_HEAD)).thenReturn(true);

        MockMultipartFile file = new MockMultipartFile(
                "file", "du_toan.pdf", "application/pdf", createValidPdfBytes());

        LedgerEvidenceUploadResponse branchRes = service.uploadEvidence(file, null);
        assertThat(branchRes.evidenceUrl()).contains("/api/v1/ledger/evidence/");

        when(currentUserProvider.hasAuthority(SystemRoles.BRANCH_HEAD)).thenReturn(false);
        when(currentUserProvider.currentMemberId()).thenReturn(Optional.of("MEM_EVT_TR"));
        when(eventQueryService.hasEventRoleInClan("CLAN_01", "MEM_EVT_TR", EventRole.EventTreasurer))
                .thenReturn(true);

        LedgerEvidenceUploadResponse treasurerRes = service.uploadEvidence(file, null);
        assertThat(treasurerRes.evidenceUrl()).contains("/api/v1/ledger/evidence/");

        when(currentUserProvider.currentMemberId()).thenReturn(Optional.of("MEM_NORMAL"));
        when(eventQueryService.hasEventRoleInClan("CLAN_01", "MEM_NORMAL", EventRole.EventTreasurer))
                .thenReturn(false);

        assertThatThrownBy(() -> service.uploadEvidence(file, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("downloadEvidence: Kiểm tra tên file chuẩn UUID và cô lập đường dẫn theo ClanId")
    void downloadEvidenceValidatesFilenameAndTenantKey() {
        assertThatThrownBy(() -> service.downloadEvidence("../secret.pdf"))
                .isInstanceOf(MediaException.class)
                .satisfies(ex -> assertThat(((MediaException) ex).getStatus()).isEqualTo(400));

        String validName = "550e8400-e29b-41d4-a716-446655440000.pdf";
        when(fileStorage.open("member-media", "CLAN_01/ledger-evidence/" + validName))
                .thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));

        LedgerEvidenceService.EvidenceDownload download = service.downloadEvidence(validName);
        assertThat(download.fileName()).isEqualTo(validName);
        assertThat(download.mimeType()).isEqualTo("application/pdf");
    }
}
