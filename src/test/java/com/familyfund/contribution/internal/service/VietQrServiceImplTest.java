package com.familyfund.contribution.internal.service;

import com.familyfund.contribution.api.dto.PaymentMethod;
import com.familyfund.contribution.internal.dto.request.GenerateVietQrRequest;
import com.familyfund.contribution.internal.dto.request.VietQrWebhookPayload;
import com.familyfund.contribution.internal.dto.response.ElectronicReceiptResponse;
import com.familyfund.contribution.internal.dto.response.VietQrResponse;
import com.familyfund.contribution.internal.dto.response.WebhookPaymentResultResponse;
import com.familyfund.contribution.internal.entity.CashContribution;
import com.familyfund.contribution.internal.entity.PaymentWebhook;
import com.familyfund.contribution.internal.repository.CashContributionRepository;
import com.familyfund.contribution.internal.repository.PaymentWebhookRepository;
import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.event.api.EventQueryService;
import com.familyfund.event.api.dto.EventStatus;
import com.familyfund.event.api.dto.EventSummaryDto;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.genealogy.api.dto.MemberSummaryDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VietQrServiceImplTest {

    @Mock
    private CashContributionService cashContributionService;

    @Mock
    private CashContributionRepository cashContributionRepository;

    @Mock
    private PaymentWebhookRepository paymentWebhookRepository;

    @Mock
    private EventQueryService eventQueryService;

    @Mock
    private GenealogyQueryService genealogyQueryService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private VietQrServiceImpl vietQrService;

    private final String eventId = "EVT01";
    private final String memberId = "MEM01";
    private final String clanId = "CLAN01";

    @BeforeEach
    void setUp() {
        TenantContext.setClanId(clanId);
        ReflectionTestUtils.setField(vietQrService, "bankBin", "970436");
        ReflectionTestUtils.setField(vietQrService, "accountNumber", "1023456789");
        ReflectionTestUtils.setField(vietQrService, "accountName", "QUY DONG HO GIA TOC");
        ReflectionTestUtils.setField(vietQrService, "template", "compact2");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("generateVietQr - generates standardized QR with FFUND eventId memberId")
    void generateVietQr_success() {
        when(eventQueryService.findEventSummary(eventId))
                .thenReturn(Optional.of(new EventSummaryDto(eventId, clanId, "Lễ Giỗ Tổ", EventStatus.Draft, Instant.now())));
        when(genealogyQueryService.findMemberSummary(memberId))
                .thenReturn(Optional.of(new MemberSummaryDto(memberId, "Nguyễn Văn A", clanId, "BR01")));

        GenerateVietQrRequest request = new GenerateVietQrRequest(
                eventId, memberId, new BigDecimal("500000.00"), "Cúng giỗ"
        );

        VietQrResponse response = vietQrService.generateVietQr(request);

        assertThat(response).isNotNull();
        assertThat(response.transferContent()).isEqualTo("FFUND EVT01 MEM01");
        assertThat(response.amount()).isEqualByComparingTo("500000.00");
        assertThat(response.qrCodeUrl()).contains("970436-1023456789-compact2.png");
        assertThat(response.qrCodeUrl()).contains("FFUND+EVT01+MEM01");
        assertThat(response.bankName()).isEqualTo("Vietcombank");
    }

    @Test
    @DisplayName("generateVietQr - throws exception when event is closed")
    void generateVietQr_eventClosed_throwsException() {
        when(eventQueryService.findEventSummary(eventId))
                .thenReturn(Optional.of(new EventSummaryDto(eventId, clanId, "Lễ Giỗ Tổ", EventStatus.Closed, Instant.now())));

        GenerateVietQrRequest request = new GenerateVietQrRequest(
                eventId, memberId, new BigDecimal("500000.00"), "Cúng giỗ"
        );

        assertThatThrownBy(() -> vietQrService.generateVietQr(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("closed event");
    }

    @Test
    @DisplayName("processPaymentWebhook - parses standard content and records bank transfer contribution")
    void processPaymentWebhook_success() {
        String refCode = "FT26110900123";
        VietQrWebhookPayload payload = new VietQrWebhookPayload(
                "Vietcombank", "2026-11-09", "1023456789", refCode,
                "Chuyen khoan FFUND EVT01 MEM01 ung ho dong ho", "in",
                new BigDecimal("500000.00"), refCode
        );

        when(paymentWebhookRepository.findByReferenceCode(refCode)).thenReturn(Optional.empty());
        when(eventQueryService.findEventSummary(eventId))
                .thenReturn(Optional.of(new EventSummaryDto(eventId, clanId, "Lễ Giỗ Tổ", EventStatus.Draft, Instant.now())));
        when(genealogyQueryService.findMemberSummary(memberId))
                .thenReturn(Optional.of(new MemberSummaryDto(memberId, "Nguyễn Văn A", clanId, "BR01")));

        ElectronicReceiptResponse receipt = new ElectronicReceiptResponse(
                "PT-20261109-00001", "CTB01", eventId, "Lễ Giỗ Tổ", memberId, "Nguyễn Văn A",
                "ACC01", new BigDecimal("500000.00"), "Năm trăm nghìn đồng chẵn", PaymentMethod.BankTransfer,
                Instant.now(), Collections.emptyList()
        );

        when(cashContributionService.recordCashContribution(any())).thenReturn(receipt);

        CashContribution cash = CashContribution.builder()
                .contributionId("CTB01")
                .amount(new BigDecimal("500000.00"))
                .paymentMethod(PaymentMethod.BankTransfer)
                .build();
        when(cashContributionRepository.findById("CTB01")).thenReturn(Optional.of(cash));

        WebhookPaymentResultResponse result = vietQrService.processPaymentWebhook(payload);

        assertThat(result.success()).isTrue();
        assertThat(result.referenceCode()).isEqualTo(refCode);
        assertThat(result.receiptNumber()).isEqualTo("PT-20261109-00001");
        assertThat(cash.getIsReconciled()).isTrue();
        assertThat(cash.getBankTransactionCode()).isEqualTo(refCode);

        verify(paymentWebhookRepository).save(any(PaymentWebhook.class));
        verify(cashContributionRepository).save(cash);
    }

    @Test
    @DisplayName("processPaymentWebhook - idempotent when reference code is already processed")
    void processPaymentWebhook_idempotentDuplicate() {
        String refCode = "FT_ALREADY_DONE";
        PaymentWebhook existing = PaymentWebhook.builder()
                .webhookId("WH_01")
                .referenceCode(refCode)
                .contributionId("CTB_PREV")
                .status("Processed")
                .build();

        when(paymentWebhookRepository.findByReferenceCode(refCode)).thenReturn(Optional.of(existing));

        VietQrWebhookPayload payload = new VietQrWebhookPayload(
                "Vietcombank", null, null, refCode, "content", "in", new BigDecimal("100000"), refCode
        );

        WebhookPaymentResultResult(existing, payload, refCode);
    }

    private void WebhookPaymentResultResult(PaymentWebhook existing, VietQrWebhookPayload payload, String refCode) {
        WebhookPaymentResultResponse result = vietQrService.processPaymentWebhook(payload);
        assertThat(result.success()).isTrue();
        assertThat(result.message()).contains("already processed");
        assertThat(result.contributionId()).isEqualTo("CTB_PREV");
    }
}
