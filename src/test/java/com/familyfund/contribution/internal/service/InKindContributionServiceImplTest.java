package com.familyfund.contribution.internal.service;

import com.familyfund.contribution.api.dto.ContributionType;
import com.familyfund.contribution.api.dto.ConversionMethod;
import com.familyfund.contribution.api.dto.InKindProcessingStatus;
import com.familyfund.contribution.internal.dto.request.MemberAllocationRequest;
import com.familyfund.contribution.internal.dto.request.RecordInKindContributionRequest;
import com.familyfund.contribution.internal.dto.response.InKindContributionDetailResponse;
import com.familyfund.contribution.internal.dto.response.InKindElectronicReceiptResponse;
import com.familyfund.contribution.internal.entity.Contribution;
import com.familyfund.contribution.internal.entity.ContributionMemberDetail;
import com.familyfund.contribution.internal.entity.EventItemBenchmark;
import com.familyfund.contribution.internal.entity.InKindContribution;
import com.familyfund.contribution.internal.repository.ContributionMemberDetailRepository;
import com.familyfund.contribution.internal.repository.ContributionRepository;
import com.familyfund.contribution.internal.repository.EventItemBenchmarkRepository;
import com.familyfund.contribution.internal.repository.InKindContributionRepository;
import com.familyfund.contribution.internal.util.ContributionIdGenerator;
import com.familyfund.contribution.internal.util.ReceiptNumberGenerator;
import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.core.exception.ResourceNotFoundException;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InKindContributionServiceImplTest {

    @Mock
    private ContributionRepository contributionRepository;

    @Mock
    private InKindContributionRepository inKindContributionRepository;

    @Mock
    private ContributionMemberDetailRepository contributionMemberDetailRepository;

    @Mock
    private EventItemBenchmarkRepository benchmarkRepository;

    @Mock
    private ContributionIdGenerator contributionIdGenerator;

    @Mock
    private ReceiptNumberGenerator receiptNumberGenerator;

    @Mock
    private GenealogyQueryService genealogyQueryService;

    @Mock
    private EventQueryService eventQueryService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private InKindContributionServiceImpl inKindContributionService;

    private static final String CLAN_ID = "CLAN_01";
    private static final String EVENT_ID = "EVT_001";
    private static final String PAYER_ID = "MEM_001";
    private static final String RECEIVER_ACC_ID = "ACC_TREASURER";

    @BeforeEach
    void setUp() {
        TenantContext.setClanId(CLAN_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Should successfully record in-kind contribution with single payer and issue receipt")
    void recordInKindContribution_singlePayer_success() {
        RecordInKindContributionRequest request = new RecordInKindContributionRequest(
                EVENT_ID,
                PAYER_ID,
                "Bộ lư đồng tam sự",
                BigDecimal.ONE,
                "bộ",
                new BigDecimal("15000000"),
                ConversionMethod.Agreement,
                new BigDecimal("15000000"),
                null,
                null,
                null
        );

        EventSummaryDto eventSummary = new EventSummaryDto(EVENT_ID, CLAN_ID, "Giỗ Tổ 2026", EventStatus.Ongoing, Instant.now());
        MemberSummaryDto payerSummary = new MemberSummaryDto(PAYER_ID, "Nguyễn Văn A", CLAN_ID, "Chi 1");

        when(eventQueryService.findEventSummary(EVENT_ID)).thenReturn(Optional.of(eventSummary));
        when(genealogyQueryService.findMemberSummary(PAYER_ID)).thenReturn(Optional.of(payerSummary));
        when(currentUserProvider.currentAccountId()).thenReturn(Optional.of(RECEIVER_ACC_ID));
        when(contributionIdGenerator.generate()).thenReturn("CTB12345678");
        when(receiptNumberGenerator.generate()).thenReturn("PT-20261031-123456");
        when(contributionRepository.existsById(anyString())).thenReturn(false);
        when(inKindContributionRepository.existsByReceiptNumber(anyString())).thenReturn(false);

        InKindElectronicReceiptResponse response = inKindContributionService.recordInKindContribution(request);

        assertThat(response).isNotNull();
        assertThat(response.receiptNumber()).isEqualTo("PT-20261031-123456");
        assertThat(response.contributionId()).isEqualTo("CTB12345678");
        assertThat(response.itemName()).isEqualTo("Bộ lư đồng tam sự");
        assertThat(response.quantity()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(response.unit()).isEqualTo("bộ");
        assertThat(response.convertedMonetaryValue()).isEqualByComparingTo(new BigDecimal("15000000"));
        assertThat(response.conversionMethod()).isEqualTo(ConversionMethod.Agreement);
        assertThat(response.processingStatus()).isEqualTo(InKindProcessingStatus.Pending);
        assertThat(response.allocations()).hasSize(1);
        assertThat(response.allocations().get(0).memberId()).isEqualTo(PAYER_ID);

        verify(contributionRepository).save(any(Contribution.class));
        verify(inKindContributionRepository).save(any(InKindContribution.class));
        verify(contributionMemberDetailRepository).saveAll(any());
    }

    @Test
    @DisplayName("Should successfully record in-kind contribution with BtcBenchmark and match unit price * quantity")
    void recordInKindContribution_btcBenchmark_success() {
        RecordInKindContributionRequest request = new RecordInKindContributionRequest(
                EVENT_ID,
                PAYER_ID,
                "Heo quay",
                new BigDecimal("2.0"),
                "con",
                BigDecimal.ZERO,
                ConversionMethod.BtcBenchmark,
                new BigDecimal("7000000"),
                "BMK_001",
                null,
                null
        );

        EventSummaryDto eventSummary = new EventSummaryDto(EVENT_ID, CLAN_ID, "Giỗ Tổ 2026", EventStatus.Ongoing, Instant.now());
        MemberSummaryDto payerSummary = new MemberSummaryDto(PAYER_ID, "Nguyễn Văn A", CLAN_ID, "Chi 1");
        EventItemBenchmark benchmark = EventItemBenchmark.builder()
                .benchmarkId("BMK_001")
                .eventId(EVENT_ID)
                .clanId(CLAN_ID)
                .itemName("Heo quay")
                .unit("con")
                .unitPrice(new BigDecimal("3500000"))
                .build();

        when(eventQueryService.findEventSummary(EVENT_ID)).thenReturn(Optional.of(eventSummary));
        when(genealogyQueryService.findMemberSummary(PAYER_ID)).thenReturn(Optional.of(payerSummary));
        when(currentUserProvider.currentAccountId()).thenReturn(Optional.of(RECEIVER_ACC_ID));
        when(benchmarkRepository.findByBenchmarkIdAndEventId("BMK_001", EVENT_ID)).thenReturn(Optional.of(benchmark));
        when(contributionIdGenerator.generate()).thenReturn("CTB12345678");
        when(receiptNumberGenerator.generate()).thenReturn("PT-20261031-123456");

        InKindElectronicReceiptResponse response = inKindContributionService.recordInKindContribution(request);

        assertThat(response.convertedMonetaryValue()).isEqualByComparingTo(new BigDecimal("7000000"));
        assertThat(response.conversionMethod()).isEqualTo(ConversionMethod.BtcBenchmark);
    }

    @Test
    @DisplayName("Should throw BusinessRuleException when converted value does not match benchmark unit price * quantity")
    void recordInKindContribution_btcBenchmark_mismatchedValue_throwsBusinessRuleException() {
        RecordInKindContributionRequest request = new RecordInKindContributionRequest(
                EVENT_ID,
                PAYER_ID,
                "Heo quay",
                new BigDecimal("2.0"),
                "con",
                BigDecimal.ZERO,
                ConversionMethod.BtcBenchmark,
                new BigDecimal("8000000"), // Mismatched: 2 * 3.5m = 7m
                "BMK_001",
                null,
                null
        );

        EventSummaryDto eventSummary = new EventSummaryDto(EVENT_ID, CLAN_ID, "Giỗ Tổ 2026", EventStatus.Ongoing, Instant.now());
        MemberSummaryDto payerSummary = new MemberSummaryDto(PAYER_ID, "Nguyễn Văn A", CLAN_ID, "Chi 1");
        EventItemBenchmark benchmark = EventItemBenchmark.builder()
                .benchmarkId("BMK_001")
                .eventId(EVENT_ID)
                .clanId(CLAN_ID)
                .itemName("Heo quay")
                .unit("con")
                .unitPrice(new BigDecimal("3500000"))
                .build();

        when(eventQueryService.findEventSummary(EVENT_ID)).thenReturn(Optional.of(eventSummary));
        when(genealogyQueryService.findMemberSummary(PAYER_ID)).thenReturn(Optional.of(payerSummary));
        when(currentUserProvider.currentAccountId()).thenReturn(Optional.of(RECEIVER_ACC_ID));
        when(benchmarkRepository.findByBenchmarkIdAndEventId("BMK_001", EVENT_ID)).thenReturn(Optional.of(benchmark));

        assertThatThrownBy(() -> inKindContributionService.recordInKindContribution(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("does not match benchmark unit price");
    }

    @Test
    @DisplayName("Should successfully record in-kind contribution with Invoice and multiple allocations")
    void recordInKindContribution_invoiceWithAllocations_success() {
        String member2 = "MEM_002";
        RecordInKindContributionRequest request = new RecordInKindContributionRequest(
                EVENT_ID,
                PAYER_ID,
                "Mâm hoa quả nhập khẩu",
                new BigDecimal("1.0"),
                "mâm",
                new BigDecimal("2000000"),
                ConversionMethod.Invoice,
                new BigDecimal("2000000"),
                null,
                "HĐ-888999",
                List.of(
                        new MemberAllocationRequest(PAYER_ID, new BigDecimal("1200000")),
                        new MemberAllocationRequest(member2, new BigDecimal("800000"))
                )
        );

        EventSummaryDto eventSummary = new EventSummaryDto(EVENT_ID, CLAN_ID, "Giỗ Tổ 2026", EventStatus.Ongoing, Instant.now());
        MemberSummaryDto payerSummary = new MemberSummaryDto(PAYER_ID, "Nguyễn Văn A", CLAN_ID, "Chi 1");
        MemberSummaryDto member2Summary = new MemberSummaryDto(member2, "Nguyễn Văn B", CLAN_ID, "Chi 1");

        when(eventQueryService.findEventSummary(EVENT_ID)).thenReturn(Optional.of(eventSummary));
        when(genealogyQueryService.findMemberSummary(PAYER_ID)).thenReturn(Optional.of(payerSummary));
        when(genealogyQueryService.findMemberSummary(member2)).thenReturn(Optional.of(member2Summary));
        when(currentUserProvider.currentAccountId()).thenReturn(Optional.of(RECEIVER_ACC_ID));
        when(contributionIdGenerator.generate()).thenReturn("CTB12345678");
        when(receiptNumberGenerator.generate()).thenReturn("PT-20261031-123456");

        InKindElectronicReceiptResponse response = inKindContributionService.recordInKindContribution(request);

        assertThat(response.allocations()).hasSize(2);
        assertThat(response.conversionMethod()).isEqualTo(ConversionMethod.Invoice);
    }

    @Test
    @DisplayName("Should throw BusinessRuleException when total allocated does not match converted monetary value")
    void recordInKindContribution_allocationSumMismatch_throwsBusinessRuleException() {
        RecordInKindContributionRequest request = new RecordInKindContributionRequest(
                EVENT_ID,
                PAYER_ID,
                "Heo quay",
                BigDecimal.ONE,
                "con",
                BigDecimal.ZERO,
                ConversionMethod.Agreement,
                new BigDecimal("3000000"),
                null,
                null,
                List.of(
                        new MemberAllocationRequest(PAYER_ID, new BigDecimal("1000000")),
                        new MemberAllocationRequest("MEM_002", new BigDecimal("1500000")) // Sum = 2.5m != 3.0m
                )
        );

        EventSummaryDto eventSummary = new EventSummaryDto(EVENT_ID, CLAN_ID, "Giỗ Tổ 2026", EventStatus.Ongoing, Instant.now());
        MemberSummaryDto payerSummary = new MemberSummaryDto(PAYER_ID, "Nguyễn Văn A", CLAN_ID, "Chi 1");

        when(eventQueryService.findEventSummary(EVENT_ID)).thenReturn(Optional.of(eventSummary));
        when(genealogyQueryService.findMemberSummary(PAYER_ID)).thenReturn(Optional.of(payerSummary));
        when(currentUserProvider.currentAccountId()).thenReturn(Optional.of(RECEIVER_ACC_ID));

        assertThatThrownBy(() -> inKindContributionService.recordInKindContribution(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("does not match converted monetary value");
    }

    @Test
    @DisplayName("Should throw BusinessRuleException when recording contribution for a closed event")
    void recordInKindContribution_closedEvent_throwsBusinessRuleException() {
        RecordInKindContributionRequest request = new RecordInKindContributionRequest(
                EVENT_ID,
                PAYER_ID,
                "Lư hương",
                BigDecimal.ONE,
                "cái",
                BigDecimal.ZERO,
                ConversionMethod.Agreement,
                new BigDecimal("1000000"),
                null,
                null,
                null
        );

        EventSummaryDto closedEvent = new EventSummaryDto(EVENT_ID, CLAN_ID, "Giỗ Tổ 2026", EventStatus.Closed, Instant.now());
        when(eventQueryService.findEventSummary(EVENT_ID)).thenReturn(Optional.of(closedEvent));

        assertThatThrownBy(() -> inKindContributionService.recordInKindContribution(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Cannot record contribution for a closed event");
    }

    @Test
    @DisplayName("Should retrieve receipt by receipt number successfully")
    void getReceiptByReceiptNumber_success() {
        String receiptNumber = "PT-20261031-123456";
        String contributionId = "CTB_001";

        InKindContribution inKind = InKindContribution.builder()
                .contributionId(contributionId)
                .itemName("Đỉnh đồng")
                .quantity(BigDecimal.ONE)
                .unit("chiếc")
                .conversionMethod(ConversionMethod.Agreement)
                .convertedMonetaryValue(new BigDecimal("10000000"))
                .processingStatus(InKindProcessingStatus.Pending)
                .receiptNumber(receiptNumber)
                .build();

        Contribution contribution = Contribution.builder()
                .contributionId(contributionId)
                .eventId(EVENT_ID)
                .payerMemberId(PAYER_ID)
                .receiverAccountId(RECEIVER_ACC_ID)
                .contributionType(ContributionType.InKind)
                .totalMonetaryValue(new BigDecimal("10000000"))
                .contributionDate(Instant.now())
                .build();

        EventSummaryDto eventSummary = new EventSummaryDto(EVENT_ID, CLAN_ID, "Giỗ Tổ 2026", EventStatus.Ongoing, Instant.now());
        MemberSummaryDto payerSummary = new MemberSummaryDto(PAYER_ID, "Nguyễn Văn A", CLAN_ID, "Chi 1");

        when(inKindContributionRepository.findByReceiptNumber(receiptNumber)).thenReturn(Optional.of(inKind));
        when(contributionRepository.findById(contributionId)).thenReturn(Optional.of(contribution));
        when(eventQueryService.findEventSummary(EVENT_ID)).thenReturn(Optional.of(eventSummary));
        when(genealogyQueryService.findMemberSummary(PAYER_ID)).thenReturn(Optional.of(payerSummary));
        when(contributionMemberDetailRepository.findAllByContributionId(contributionId)).thenReturn(List.of(
                ContributionMemberDetail.builder().contributionId(contributionId).memberId(PAYER_ID).recordedAmount(new BigDecimal("10000000")).build()
        ));

        InKindElectronicReceiptResponse response = inKindContributionService.getReceiptByReceiptNumber(receiptNumber);

        assertThat(response).isNotNull();
        assertThat(response.receiptNumber()).isEqualTo(receiptNumber);
        assertThat(response.itemName()).isEqualTo("Đỉnh đồng");
    }
}
