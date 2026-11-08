package com.familyfund.contribution.internal.service;

import com.familyfund.contribution.api.dto.ContributionType;
import com.familyfund.contribution.api.dto.PaymentMethod;
import com.familyfund.contribution.internal.dto.response.ElectronicReceiptResponse;
import com.familyfund.contribution.internal.dto.response.MemberContributionHistoryResponse;
import com.familyfund.contribution.internal.entity.CashContribution;
import com.familyfund.contribution.internal.entity.Contribution;
import com.familyfund.contribution.internal.entity.ContributionMemberDetail;
import com.familyfund.contribution.internal.repository.CashContributionRepository;
import com.familyfund.contribution.internal.repository.ContributionMemberDetailRepository;
import com.familyfund.contribution.internal.repository.ContributionRepository;
import com.familyfund.contribution.internal.repository.InKindContributionRepository;
import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.event.api.EventQueryService;
import com.familyfund.event.api.dto.EventStatus;
import com.familyfund.event.api.dto.EventSummaryDto;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.genealogy.api.dto.MemberSummaryDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContributionHistoryServiceImplTest {

    @Mock
    private ContributionRepository contributionRepository;

    @Mock
    private CashContributionRepository cashContributionRepository;

    @Mock
    private InKindContributionRepository inKindContributionRepository;

    @Mock
    private ContributionMemberDetailRepository contributionMemberDetailRepository;

    @Mock
    private CashContributionService cashContributionService;

    @Mock
    private InKindContributionService inKindContributionService;

    @Mock
    private ReceiptPdfService receiptPdfService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private GenealogyQueryService genealogyQueryService;

    @Mock
    private EventQueryService eventQueryService;

    @InjectMocks
    private ContributionHistoryServiceImpl contributionHistoryService;

    private final String currentMemberId = "MEM01";
    private final String clanId = "CLAN01";
    private final String branchId = "BR01";
    private final String eventId = "EVT01";

    @BeforeEach
    void setUp() {
    }

    @Test
    @DisplayName("getMyContributionHistory - success for personal history only")
    void getMyContributionHistory_personalOnly_success() {
        when(currentUserProvider.currentMemberId()).thenReturn(Optional.of(currentMemberId));
        when(genealogyQueryService.findMemberSummary(currentMemberId))
                .thenReturn(Optional.of(new MemberSummaryDto(currentMemberId, "Nguyễn Văn A", clanId, branchId)));

        Contribution contribution = Contribution.builder()
                .contributionId("CTB01")
                .eventId(eventId)
                .payerMemberId(currentMemberId)
                .receiverAccountId("ACC01")
                .contributionType(ContributionType.Cash)
                .totalMonetaryValue(new BigDecimal("1000000.00"))
                .contributionDate(Instant.now())
                .build();

        CashContribution cash = CashContribution.builder()
                .contributionId("CTB01")
                .amount(new BigDecimal("1000000.00"))
                .paymentMethod(PaymentMethod.Cash)
                .receiptNumber("PT-20261109-00001")
                .build();

        when(contributionRepository.findByPayerMemberIdIn(any())).thenReturn(List.of(contribution));
        when(contributionMemberDetailRepository.findAllByMemberIdIn(any())).thenReturn(Collections.emptyList());
        when(cashContributionRepository.findById("CTB01")).thenReturn(Optional.of(cash));
        when(contributionMemberDetailRepository.findAllByContributionId("CTB01")).thenReturn(Collections.emptyList());
        when(eventQueryService.findEventSummary(eventId)).thenReturn(Optional.of(new EventSummaryDto(eventId, clanId, "Lễ Giỗ Tổ", EventStatus.Draft, Instant.now())));

        MemberContributionHistoryResponse response = contributionHistoryService.getMyContributionHistory(false);

        assertThat(response).isNotNull();
        assertThat(response.memberId()).isEqualTo(currentMemberId);
        assertThat(response.memberName()).isEqualTo("Nguyễn Văn A");
        assertThat(response.totalPersonalContributed()).isEqualByComparingTo("1000000.00");
        assertThat(response.totalAllocated()).isEqualByComparingTo("1000000.00");
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).receiptNumber()).isEqualTo("PT-20261109-00001");
        assertThat(response.items().get(0).eventName()).isEqualTo("Lễ Giỗ Tổ");
    }

    @Test
    @DisplayName("getMyContributionHistory - throws exception when user is not linked to any member")
    void getMyContributionHistory_notLinked_throwsException() {
        when(currentUserProvider.currentMemberId()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> contributionHistoryService.getMyContributionHistory(false))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not linked to any member");
    }

    @Test
    @DisplayName("downloadReceiptPdf - generates PDF for cash receipt")
    void downloadReceiptPdf_cashReceipt_success() {
        String receiptNumber = "PT-20261109-00001";
        byte[] expectedPdf = new byte[]{1, 2, 3, 4};

        ElectronicReceiptResponse receipt = new ElectronicReceiptResponse(
                receiptNumber, "CTB01", eventId, "Lễ Giỗ Tổ", currentMemberId, "Nguyễn Văn A",
                "ACC01", new BigDecimal("1000000.00"), "Một triệu đồng chẵn", PaymentMethod.Cash,
                Instant.now(), Collections.emptyList()
        );

        when(cashContributionRepository.existsByReceiptNumber(receiptNumber)).thenReturn(true);
        when(cashContributionService.getReceiptByReceiptNumber(receiptNumber)).thenReturn(receipt);
        when(receiptPdfService.generateCashReceiptPdf(eq(receipt), any())).thenReturn(expectedPdf);

        byte[] result = contributionHistoryService.downloadReceiptPdf(receiptNumber);

        assertThat(result).isEqualTo(expectedPdf);
    }

    @Test
    @DisplayName("downloadReceiptPdf - throws 404 if receipt does not exist")
    void downloadReceiptPdf_notFound_throwsException() {
        String receiptNumber = "PT-NOT-FOUND";
        when(cashContributionRepository.existsByReceiptNumber(receiptNumber)).thenReturn(false);
        when(inKindContributionRepository.existsByReceiptNumber(receiptNumber)).thenReturn(false);

        assertThatThrownBy(() -> contributionHistoryService.downloadReceiptPdf(receiptNumber))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not found");
    }
}
