package com.familyfund.contribution.internal.service;

import com.familyfund.contribution.api.dto.PaymentMethod;
import com.familyfund.contribution.internal.dto.request.MemberAllocationRequest;
import com.familyfund.contribution.internal.dto.request.RecordCashContributionRequest;
import com.familyfund.contribution.internal.dto.response.ElectronicReceiptResponse;
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
class CashContributionServiceImplTest {

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
    private CashContributionServiceImpl service;

    private final String clanId = "CLAN_TEST";
    private final String eventId = "EVT_001";
    private final String payerId = "MEM_PAYER";
    private final String receiverId = "ACC_TREASURER";

    @BeforeEach
    void setUp() {
        TenantContext.setClanId(clanId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Single payer contribution: Should allocate 100% amount to payer and generate e-receipt")
    void recordCashContribution_singlePayer_success() {
        BigDecimal amount = BigDecimal.valueOf(1000000);
        RecordCashContributionRequest request = new RecordCashContributionRequest(
                eventId, payerId, amount, PaymentMethod.Cash, null
        );

        EventSummaryDto eventSummary = new EventSummaryDto(eventId, clanId, "Lễ Giỗ Tổ", EventStatus.Ongoing, Instant.now());
        MemberSummaryDto payerSummary = new MemberSummaryDto(payerId, "Nguyễn Văn A", clanId, "BR_01");

        when(eventQueryService.findEventSummary(eventId)).thenReturn(Optional.of(eventSummary));
        when(genealogyQueryService.findMemberSummary(payerId)).thenReturn(Optional.of(payerSummary));
        when(currentUserProvider.currentAccountId()).thenReturn(Optional.of(receiverId));
        when(contributionIdGenerator.generate()).thenReturn("CTB_001");
        when(receiptNumberGenerator.generate()).thenReturn("PT-20261029-A1B2C3");
        when(contributionRepository.existsById(anyString())).thenReturn(false);
        when(cashContributionRepository.existsByReceiptNumber(anyString())).thenReturn(false);

        ElectronicReceiptResponse response = service.recordCashContribution(request);

        assertThat(response).isNotNull();
        assertThat(response.receiptNumber()).isEqualTo("PT-20261029-A1B2C3");
        assertThat(response.contributionId()).isEqualTo("CTB_001");
        assertThat(response.amount()).isEqualTo(amount);
        assertThat(response.amountInWords()).contains("Một triệu đồng chẵn");
        assertThat(response.payerMemberName()).isEqualTo("Nguyễn Văn A");
        assertThat(response.allocations()).hasSize(1);
        assertThat(response.allocations().get(0).memberId()).isEqualTo(payerId);
        assertThat(response.allocations().get(0).recordedAmount()).isEqualTo(amount);

        verify(contributionRepository, times(1)).save(any(Contribution.class));
        verify(cashContributionRepository, times(1)).save(any(CashContribution.class));
        verify(contributionMemberDetailRepository, times(1)).saveAll(any());
    }

    @Test
    @DisplayName("Joint contribution: Should validate allocation sum and save all member details")
    void recordCashContribution_jointContribution_success() {
        BigDecimal totalAmount = BigDecimal.valueOf(1500000);
        String member1 = "MEM_CON_1";
        String member2 = "MEM_CON_2";

        List<MemberAllocationRequest> allocations = List.of(
                new MemberAllocationRequest(member1, BigDecimal.valueOf(500000)),
                new MemberAllocationRequest(member2, BigDecimal.valueOf(1000000))
        );

        RecordCashContributionRequest request = new RecordCashContributionRequest(
                eventId, payerId, totalAmount, PaymentMethod.BankTransfer, allocations
        );

        EventSummaryDto eventSummary = new EventSummaryDto(eventId, clanId, "Lễ Giỗ Tổ", EventStatus.Ongoing, Instant.now());
        MemberSummaryDto payerSummary = new MemberSummaryDto(payerId, "Nguyễn Văn Cha", clanId, "BR_01");
        MemberSummaryDto con1Summary = new MemberSummaryDto(member1, "Nguyễn Con Một", clanId, "BR_01");
        MemberSummaryDto con2Summary = new MemberSummaryDto(member2, "Nguyễn Con Hai", clanId, "BR_01");

        when(eventQueryService.findEventSummary(eventId)).thenReturn(Optional.of(eventSummary));
        when(genealogyQueryService.findMemberSummary(payerId)).thenReturn(Optional.of(payerSummary));
        when(genealogyQueryService.findMemberSummary(member1)).thenReturn(Optional.of(con1Summary));
        when(genealogyQueryService.findMemberSummary(member2)).thenReturn(Optional.of(con2Summary));
        when(currentUserProvider.currentAccountId()).thenReturn(Optional.of(receiverId));
        when(contributionIdGenerator.generate()).thenReturn("CTB_002");
        when(receiptNumberGenerator.generate()).thenReturn("PT-20261029-X9Y8Z7");

        ElectronicReceiptResponse response = service.recordCashContribution(request);

        assertThat(response).isNotNull();
        assertThat(response.receiptNumber()).isEqualTo("PT-20261029-X9Y8Z7");
        assertThat(response.amount()).isEqualTo(totalAmount);
        assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.BankTransfer);
        assertThat(response.allocations()).hasSize(2);
        assertThat(response.allocations().get(0).recordedAmount()).isEqualTo(BigDecimal.valueOf(500000));
        assertThat(response.allocations().get(1).recordedAmount()).isEqualTo(BigDecimal.valueOf(1000000));

        verify(contributionMemberDetailRepository, times(1)).saveAll(any());
    }

    @Test
    @DisplayName("Should throw BusinessRuleException when event is Closed")
    void recordCashContribution_closedEvent_throwsException() {
        RecordCashContributionRequest request = new RecordCashContributionRequest(
                eventId, payerId, BigDecimal.valueOf(500000), PaymentMethod.Cash, null
        );

        EventSummaryDto closedEvent = new EventSummaryDto(eventId, clanId, "Lễ Giỗ Đã Xong", EventStatus.Closed, Instant.now());
        when(eventQueryService.findEventSummary(eventId)).thenReturn(Optional.of(closedEvent));

        assertThatThrownBy(() -> service.recordCashContribution(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("closed event");
    }

    @Test
    @DisplayName("Should throw BusinessRuleException when allocation sum does not equal total amount")
    void recordCashContribution_mismatchedAllocations_throwsException() {
        BigDecimal totalAmount = BigDecimal.valueOf(1000000);
        List<MemberAllocationRequest> allocations = List.of(
                new MemberAllocationRequest("MEM_01", BigDecimal.valueOf(600000)),
                new MemberAllocationRequest("MEM_02", BigDecimal.valueOf(300000)) // Sum = 900,000 != 1,000,000
        );

        RecordCashContributionRequest request = new RecordCashContributionRequest(
                eventId, payerId, totalAmount, PaymentMethod.Cash, allocations
        );

        EventSummaryDto eventSummary = new EventSummaryDto(eventId, clanId, "Lễ Giỗ", EventStatus.Ongoing, Instant.now());
        MemberSummaryDto payerSummary = new MemberSummaryDto(payerId, "Nguyễn Văn A", clanId, "BR_01");

        when(eventQueryService.findEventSummary(eventId)).thenReturn(Optional.of(eventSummary));
        when(genealogyQueryService.findMemberSummary(payerId)).thenReturn(Optional.of(payerSummary));
        when(currentUserProvider.currentAccountId()).thenReturn(Optional.of(receiverId));

        assertThatThrownBy(() -> service.recordCashContribution(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("does not match total contribution amount");
    }

    @Test
    @DisplayName("Should throw BusinessRuleException when duplicate members exist in allocations")
    void recordCashContribution_duplicateMembersInAllocations_throwsException() {
        BigDecimal totalAmount = BigDecimal.valueOf(1000000);
        List<MemberAllocationRequest> allocations = List.of(
                new MemberAllocationRequest("MEM_SAME", BigDecimal.valueOf(500000)),
                new MemberAllocationRequest("MEM_SAME", BigDecimal.valueOf(500000))
        );

        RecordCashContributionRequest request = new RecordCashContributionRequest(
                eventId, payerId, totalAmount, PaymentMethod.Cash, allocations
        );

        EventSummaryDto eventSummary = new EventSummaryDto(eventId, clanId, "Lễ Giỗ", EventStatus.Ongoing, Instant.now());
        MemberSummaryDto payerSummary = new MemberSummaryDto(payerId, "Nguyễn Văn A", clanId, "BR_01");

        when(eventQueryService.findEventSummary(eventId)).thenReturn(Optional.of(eventSummary));
        when(genealogyQueryService.findMemberSummary(payerId)).thenReturn(Optional.of(payerSummary));
        when(currentUserProvider.currentAccountId()).thenReturn(Optional.of(receiverId));

        assertThatThrownBy(() -> service.recordCashContribution(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Duplicate member");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when payer member not found")
    void recordCashContribution_payerNotFound_throwsException() {
        RecordCashContributionRequest request = new RecordCashContributionRequest(
                eventId, "UNKNOWN_PAYER", BigDecimal.valueOf(500000), PaymentMethod.Cash, null
        );

        EventSummaryDto eventSummary = new EventSummaryDto(eventId, clanId, "Lễ Giỗ", EventStatus.Ongoing, Instant.now());
        when(eventQueryService.findEventSummary(eventId)).thenReturn(Optional.of(eventSummary));
        when(genealogyQueryService.findMemberSummary("UNKNOWN_PAYER")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recordCashContribution(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Payer member not found");
    }
}
