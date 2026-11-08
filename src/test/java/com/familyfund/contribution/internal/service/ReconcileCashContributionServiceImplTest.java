package com.familyfund.contribution.internal.service;

import com.familyfund.contribution.api.dto.ContributionType;
import com.familyfund.contribution.api.dto.PaymentMethod;
import com.familyfund.contribution.internal.dto.request.ReconcileContributionRequest;
import com.familyfund.contribution.internal.dto.response.ReconcileContributionResponse;
import com.familyfund.contribution.internal.dto.response.UnreconciledContributionResponse;
import com.familyfund.contribution.internal.entity.CashContribution;
import com.familyfund.contribution.internal.entity.Contribution;
import com.familyfund.contribution.internal.repository.CashContributionRepository;
import com.familyfund.contribution.internal.repository.ContributionMemberDetailRepository;
import com.familyfund.contribution.internal.repository.ContributionRepository;
import com.familyfund.contribution.internal.util.ContributionIdGenerator;
import com.familyfund.contribution.internal.util.ReceiptNumberGenerator;
import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.event.api.EventQueryService;
import com.familyfund.event.api.dto.EventStatus;
import com.familyfund.event.api.dto.EventSummaryDto;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.genealogy.api.dto.MemberSummaryDto;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReconcileCashContributionServiceImplTest {

    @Mock
    private ContributionRepository contributionRepository;

    @Mock
    private CashContributionRepository cashContributionRepository;

    @Mock
    private ContributionMemberDetailRepository contributionMemberDetailRepository;

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
    private CashContributionServiceImpl cashContributionService;

    private final String contributionId = "CTB_BANK_01";
    private final String eventId = "EVT_01";
    private final String clanId = "CLAN_01";

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        com.familyfund.core.tenant.TenantContext.setClanId(clanId);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        com.familyfund.core.tenant.TenantContext.clear();
    }

    @Test
    @DisplayName("getUnreconciledContributions - returns list of unreconciled bank transfers")
    void getUnreconciledContributions_success() {
        Contribution contribution = Contribution.builder()
                .contributionId(contributionId)
                .eventId(eventId)
                .payerMemberId("MEM_01")
                .receiverAccountId("ACC_01")
                .contributionType(ContributionType.Cash)
                .totalMonetaryValue(new BigDecimal("2000000.00"))
                .contributionDate(Instant.now())
                .build();

        CashContribution cashContribution = CashContribution.builder()
                .contributionId(contributionId)
                .amount(new BigDecimal("2000000.00"))
                .paymentMethod(PaymentMethod.BankTransfer)
                .receiptNumber("PT-20261109-99999")
                .isReconciled(false)
                .build();

        Object[] pair = new Object[]{contribution, cashContribution};
        List<Object[]> pairs = java.util.Collections.singletonList(pair);
        when(cashContributionRepository.findUnreconciledBankTransfers(null)).thenReturn(pairs);
        when(eventQueryService.findEventSummary(eventId)).thenReturn(Optional.of(new EventSummaryDto(eventId, clanId, "Lễ Khánh Thành", EventStatus.Draft, Instant.now())));
        when(genealogyQueryService.findMemberSummary("MEM_01")).thenReturn(Optional.of(new MemberSummaryDto("MEM_01", "Trần Văn B", clanId, "BR_01")));

        List<UnreconciledContributionResponse> result = cashContributionService.getUnreconciledContributions(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).contributionId()).isEqualTo(contributionId);
        assertThat(result.get(0).isReconciled()).isFalse();
        assertThat(result.get(0).payerMemberName()).isEqualTo("Trần Văn B");
    }

    @Test
    @DisplayName("reconcileContribution - success marks reconciled with code and auditor")
    void reconcileContribution_success() {
        Contribution contribution = Contribution.builder()
                .contributionId(contributionId)
                .eventId(eventId)
                .payerMemberId("MEM_01")
                .receiverAccountId("ACC_01")
                .contributionType(ContributionType.Cash)
                .totalMonetaryValue(new BigDecimal("2000000.00"))
                .build();

        CashContribution cashContribution = CashContribution.builder()
                .contributionId(contributionId)
                .amount(new BigDecimal("2000000.00"))
                .paymentMethod(PaymentMethod.BankTransfer)
                .receiptNumber("PT-20261109-99999")
                .isReconciled(false)
                .build();

        when(contributionRepository.findById(contributionId)).thenReturn(Optional.of(contribution));
        when(cashContributionRepository.findById(contributionId)).thenReturn(Optional.of(cashContribution));
        when(eventQueryService.findEventSummary(eventId)).thenReturn(Optional.of(new EventSummaryDto(eventId, clanId, "Lễ Khánh Thành", EventStatus.Draft, Instant.now())));
        when(cashContributionRepository.findByBankTransactionCode("TXN_123456")).thenReturn(Optional.empty());
        when(currentUserProvider.currentAccountId()).thenReturn(Optional.of("ACC_AUDITOR"));

        ReconcileContributionRequest request = new ReconcileContributionRequest(
                "TXN_123456", "https://img.proof.com/1.png", "Đã khớp sao kê Vietcombank"
        );

        ReconcileContributionResponse response = cashContributionService.reconcileContribution(contributionId, request);

        assertThat(response).isNotNull();
        assertThat(response.isReconciled()).isTrue();
        assertThat(response.bankTransactionCode()).isEqualTo("TXN_123456");
        assertThat(response.reconciledBy()).isEqualTo("ACC_AUDITOR");
        assertThat(cashContribution.getIsReconciled()).isTrue();
        verify(cashContributionRepository).save(cashContribution);
    }

    @Test
    @DisplayName("reconcileContribution - throws exception if already reconciled")
    void reconcileContribution_alreadyReconciled_throwsException() {
        Contribution contribution = Contribution.builder()
                .contributionId(contributionId)
                .eventId(eventId)
                .build();

        CashContribution cashContribution = CashContribution.builder()
                .contributionId(contributionId)
                .paymentMethod(PaymentMethod.BankTransfer)
                .isReconciled(true)
                .build();

        when(contributionRepository.findById(contributionId)).thenReturn(Optional.of(contribution));
        when(cashContributionRepository.findById(contributionId)).thenReturn(Optional.of(cashContribution));
        when(eventQueryService.findEventSummary(eventId)).thenReturn(Optional.of(new EventSummaryDto(eventId, clanId, "Lễ", EventStatus.Draft, Instant.now())));

        ReconcileContributionRequest request = new ReconcileContributionRequest("TXN_123", null, null);

        assertThatThrownBy(() -> cashContributionService.reconcileContribution(contributionId, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already reconciled");
    }

    @Test
    @DisplayName("reconcileContribution - throws exception if payment method is cash")
    void reconcileContribution_cashMethod_throwsException() {
        Contribution contribution = Contribution.builder()
                .contributionId(contributionId)
                .eventId(eventId)
                .build();

        CashContribution cashContribution = CashContribution.builder()
                .contributionId(contributionId)
                .paymentMethod(PaymentMethod.Cash)
                .isReconciled(false)
                .build();

        when(contributionRepository.findById(contributionId)).thenReturn(Optional.of(contribution));
        when(cashContributionRepository.findById(contributionId)).thenReturn(Optional.of(cashContribution));
        when(eventQueryService.findEventSummary(eventId)).thenReturn(Optional.of(new EventSummaryDto(eventId, clanId, "Lễ", EventStatus.Draft, Instant.now())));

        ReconcileContributionRequest request = new ReconcileContributionRequest("TXN_123", null, null);

        assertThatThrownBy(() -> cashContributionService.reconcileContribution(contributionId, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Only contributions made via BankTransfer");
    }
}
