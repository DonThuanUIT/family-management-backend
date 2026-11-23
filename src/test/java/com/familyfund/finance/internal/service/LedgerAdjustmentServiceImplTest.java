package com.familyfund.finance.internal.service;

import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.core.security.SystemRoles;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.event.api.EventQueryService;
import com.familyfund.event.api.dto.EventRole;
import com.familyfund.finance.api.dto.LedgerAdjustmentStatus;
import com.familyfund.finance.api.dto.LedgerTransactionType;
import com.familyfund.finance.internal.dto.request.ProposeLedgerAdjustmentRequest;
import com.familyfund.finance.internal.dto.request.ReviewLedgerAdjustmentRequest;
import com.familyfund.finance.internal.dto.response.LedgerAdjustmentRequestResponse;
import com.familyfund.finance.internal.dto.response.ReviewLedgerAdjustmentResponse;
import com.familyfund.finance.internal.entity.ImmutableLedger;
import com.familyfund.finance.internal.entity.LedgerAdjustmentRequest;
import com.familyfund.finance.internal.repository.ImmutableLedgerRepository;
import com.familyfund.finance.internal.repository.LedgerAdjustmentRequestRepository;
import com.familyfund.finance.internal.util.ImmutableLedgerHashCalculator;
import com.familyfund.finance.internal.util.ImmutableLedgerIdGenerator;
import com.familyfund.finance.internal.util.LedgerAdjustmentRequestIdGenerator;
import com.familyfund.identity.api.IdentityRoleCommandService;
import com.familyfund.identity.api.dto.AccountSummaryDto;
import com.familyfund.notification.api.NotificationCategory;
import com.familyfund.notification.api.NotificationCommand;
import com.familyfund.notification.api.NotificationService;
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
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LedgerAdjustmentServiceImplTest {

    @Mock
    private LedgerAdjustmentRequestRepository ledgerAdjustmentRequestRepository;

    @Mock
    private ImmutableLedgerRepository immutableLedgerRepository;

    @Mock
    private LedgerAdjustmentRequestIdGenerator requestIdGenerator;

    @Mock
    private ImmutableLedgerIdGenerator ledgerIdGenerator;

    @Spy
    private ImmutableLedgerHashCalculator ledgerHashCalculator = new ImmutableLedgerHashCalculator();

    @Mock
    private EventQueryService eventQueryService;

    @Mock
    private IdentityRoleCommandService identityRoleCommandService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private LedgerAdjustmentServiceImpl service;

    private static final String CLAN_ID = "CLN_TEST";
    private static final String OTHER_CLAN_ID = "CLN_OTHER";
    private static final String EVENT_ID = "EVT_01";
    private static final String TX_ID = "LDG_001";
    private static final String CALLER_MEMBER_ID = "MEM_TREASURER";
    private static final String CALLER_ACCOUNT_ID = "ACC_TREASURER";
    private static final String CLAN_HEAD_ACCOUNT_ID = "ACC_CLAN_HEAD";

    private ImmutableLedger originalLedger;

    @BeforeEach
    void setUp() {
        TenantContext.setClanId(CLAN_ID);
        originalLedger = ImmutableLedger.builder()
                .transactionId(TX_ID)
                .clanId(CLAN_ID)
                .eventId(EVENT_ID)
                .transactionType(LedgerTransactionType.SurplusTransfer)
                .amount(new BigDecimal("50000000.00"))
                .runningBalance(new BigDecimal("120000000.00"))
                .description("Kết dư Đại lễ Giỗ tổ 2026")
                .createdByAccountId(CALLER_ACCOUNT_ID)
                .approvedByAccountId(CLAN_HEAD_ACCOUNT_ID)
                .recordHash("a".repeat(64))
                .createdAt(Instant.now())
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Đề xuất bù trừ số tiền dương (+) thành công bởi Thủ quỹ sự kiện (EventTreasurer)")
    void proposeAdjustment_positiveAmount_byEventTreasurer_success() {
        ProposeLedgerAdjustmentRequest request = new ProposeLedgerAdjustmentRequest(
                TX_ID,
                new BigDecimal("5000000.00"),
                "Bổ sung khoản thu kết dư bị sót chứng từ ngân hàng",
                "https://storage.familyfund.vn/evidence/bank-slip.pdf"
        );

        when(immutableLedgerRepository.findById(TX_ID)).thenReturn(Optional.of(originalLedger));
        when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
        when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(false);
        when(currentUserProvider.currentMemberId()).thenReturn(Optional.of(CALLER_MEMBER_ID));
        when(eventQueryService.getMemberRolesInEvent(EVENT_ID, CALLER_MEMBER_ID))
                .thenReturn(Set.of(EventRole.EventTreasurer));
        when(ledgerAdjustmentRequestRepository.existsByOriginalTransactionIdAndApprovalStatus(
                TX_ID, LedgerAdjustmentStatus.Pending)).thenReturn(false);
        when(currentUserProvider.currentAccountId()).thenReturn(Optional.of(CALLER_ACCOUNT_ID));
        when(requestIdGenerator.generate()).thenReturn("LAR0001TEST01");
        when(ledgerAdjustmentRequestRepository.existsById("LAR0001TEST01")).thenReturn(false);
        when(ledgerAdjustmentRequestRepository.save(any(LedgerAdjustmentRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(identityRoleCommandService.findActiveAccountsByClan(CLAN_ID)).thenReturn(List.of(
                new AccountSummaryDto(CLAN_HEAD_ACCOUNT_ID, "MEM_HEAD", "0900000001", null, "Active", Set.of(SystemRoles.CLAN_HEAD)),
                new AccountSummaryDto(CALLER_ACCOUNT_ID, CALLER_MEMBER_ID, "0900000002", null, "Active", Set.of(SystemRoles.MEMBER))
        ));

        LedgerAdjustmentRequestResponse response = service.proposeAdjustment(request);

        assertThat(response.requestId()).isEqualTo("LAR0001TEST01");
        assertThat(response.originalTransactionId()).isEqualTo(TX_ID);
        assertThat(response.clanId()).isEqualTo(CLAN_ID);
        assertThat(response.eventId()).isEqualTo(EVENT_ID);
        assertThat(response.originalTransactionType()).isEqualTo(LedgerTransactionType.SurplusTransfer);
        assertThat(response.originalTransactionAmount()).isEqualByComparingTo("50000000.00");
        assertThat(response.proposedAdjustmentAmount()).isEqualByComparingTo("5000000.00");
        assertThat(response.adjustmentReason()).isEqualTo("Bổ sung khoản thu kết dư bị sót chứng từ ngân hàng");
        assertThat(response.evidenceAttachmentUrl()).isEqualTo("https://storage.familyfund.vn/evidence/bank-slip.pdf");
        assertThat(response.proposedByAccountId()).isEqualTo(CALLER_ACCOUNT_ID);
        assertThat(response.approvalStatus()).isEqualTo(LedgerAdjustmentStatus.Pending);

        ArgumentCaptor<NotificationCommand> notifCaptor = ArgumentCaptor.forClass(NotificationCommand.class);
        verify(notificationService).notify(notifCaptor.capture());
        assertThat(notifCaptor.getValue().recipientAccountId()).isEqualTo(CLAN_HEAD_ACCOUNT_ID);
        assertThat(notifCaptor.getValue().category()).isEqualTo(NotificationCategory.Finance);
        assertThat(notifCaptor.getValue().referenceType()).isEqualTo("LEDGER_ADJUSTMENT_REQUEST");
        assertThat(notifCaptor.getValue().referenceId()).isEqualTo("LAR0001TEST01");
    }

    @Test
    @DisplayName("Đề xuất bù trừ số tiền âm (-) không kèm bằng chứng (optional null) thành công bởi HeadOfOrganizer")
    void proposeAdjustment_negativeAmount_withoutEvidence_byOrganizer_success() {
        ProposeLedgerAdjustmentRequest request = new ProposeLedgerAdjustmentRequest(
                TX_ID,
                new BigDecimal("-3500000.00"),
                "Tính nhầm hóa đơn âm thanh sân khấu",
                "   "
        );

        when(immutableLedgerRepository.findById(TX_ID)).thenReturn(Optional.of(originalLedger));
        when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
        when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(false);
        when(currentUserProvider.currentMemberId()).thenReturn(Optional.of("MEM_BTC"));
        when(eventQueryService.getMemberRolesInEvent(EVENT_ID, "MEM_BTC"))
                .thenReturn(Set.of(EventRole.HeadOfOrganizer));
        when(ledgerAdjustmentRequestRepository.existsByOriginalTransactionIdAndApprovalStatus(
                TX_ID, LedgerAdjustmentStatus.Pending)).thenReturn(false);
        when(currentUserProvider.currentAccountId()).thenReturn(Optional.of("ACC_BTC"));
        when(requestIdGenerator.generate()).thenReturn("LAR0002TEST02");
        when(ledgerAdjustmentRequestRepository.existsById("LAR0002TEST02")).thenReturn(false);
        when(ledgerAdjustmentRequestRepository.save(any(LedgerAdjustmentRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(identityRoleCommandService.findActiveAccountsByClan(CLAN_ID)).thenReturn(List.of());

        LedgerAdjustmentRequestResponse response = service.proposeAdjustment(request);

        assertThat(response.proposedAdjustmentAmount()).isEqualByComparingTo("-3500000.00");
        assertThat(response.evidenceAttachmentUrl()).isNull();
        assertThat(response.approvalStatus()).isEqualTo(LedgerAdjustmentStatus.Pending);
    }

    @Test
    @DisplayName("Đề xuất bù trừ bởi Trưởng tộc (ROLE_CLAN_HEAD) thành công ngay cả khi giao dịch không gắn eventId")
    void proposeAdjustment_withoutEventId_byClanHead_success() {
        ImmutableLedger txWithoutEvent = ImmutableLedger.builder()
                .transactionId("LDG_NO_EVT")
                .clanId(CLAN_ID)
                .eventId(null)
                .transactionType(LedgerTransactionType.AdjustmentTransaction)
                .amount(new BigDecimal("10000000.00"))
                .runningBalance(new BigDecimal("130000000.00"))
                .description("Bút toán quỹ chung")
                .createdByAccountId(CLAN_HEAD_ACCOUNT_ID)
                .approvedByAccountId(CLAN_HEAD_ACCOUNT_ID)
                .recordHash("b".repeat(64))
                .createdAt(Instant.now())
                .build();

        ProposeLedgerAdjustmentRequest request = new ProposeLedgerAdjustmentRequest(
                "LDG_NO_EVT",
                new BigDecimal("-1000000.00"),
                "Điều chỉnh bút toán sai sót sổ cái",
                null
        );

        when(immutableLedgerRepository.findById("LDG_NO_EVT")).thenReturn(Optional.of(txWithoutEvent));
        when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
        when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(true);
        when(ledgerAdjustmentRequestRepository.existsByOriginalTransactionIdAndApprovalStatus(
                "LDG_NO_EVT", LedgerAdjustmentStatus.Pending)).thenReturn(false);
        when(currentUserProvider.currentAccountId()).thenReturn(Optional.of(CLAN_HEAD_ACCOUNT_ID));
        when(requestIdGenerator.generate()).thenReturn("LAR0003TEST03");
        when(ledgerAdjustmentRequestRepository.existsById("LAR0003TEST03")).thenReturn(false);
        when(ledgerAdjustmentRequestRepository.save(any(LedgerAdjustmentRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(identityRoleCommandService.findActiveAccountsByClan(CLAN_ID)).thenReturn(List.of());

        LedgerAdjustmentRequestResponse response = service.proposeAdjustment(request);

        assertThat(response.eventId()).isNull();
        assertThat(response.approvalStatus()).isEqualTo(LedgerAdjustmentStatus.Pending);
    }

    @Test
    @DisplayName("Từ chối khi số tiền đề xuất bù trừ bằng 0 (BusinessRuleException)")
    void proposeAdjustment_zeroAmount_throwsBusinessRule() {
        ProposeLedgerAdjustmentRequest request = new ProposeLedgerAdjustmentRequest(
                TX_ID,
                new BigDecimal("0.00"),
                "Lý do không hợp lệ",
                null
        );

        assertThatThrownBy(() -> service.proposeAdjustment(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("khác 0");
    }

    @Test
    @DisplayName("Từ chối khi giao dịch gốc đã có một đề xuất Pending chờ duyệt (BusinessRuleException)")
    void proposeAdjustment_duplicatePending_throwsBusinessRule() {
        ProposeLedgerAdjustmentRequest request = new ProposeLedgerAdjustmentRequest(
                TX_ID,
                new BigDecimal("-500000.00"),
                "Đề xuất trùng lặp",
                null
        );

        when(immutableLedgerRepository.findById(TX_ID)).thenReturn(Optional.of(originalLedger));
        when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(true);
        when(ledgerAdjustmentRequestRepository.existsByOriginalTransactionIdAndApprovalStatus(
                TX_ID, LedgerAdjustmentStatus.Pending)).thenReturn(true);

        assertThatThrownBy(() -> service.proposeAdjustment(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Pending");
    }

    @Test
    @DisplayName("Từ chối với 404 khi giao dịch gốc không tồn tại hoặc thuộc dòng họ khác")
    void proposeAdjustment_crossClanOrMissing_throwsNotFound() {
        ImmutableLedger otherClanLedger = ImmutableLedger.builder()
                .transactionId(TX_ID)
                .clanId(OTHER_CLAN_ID)
                .transactionType(LedgerTransactionType.SurplusTransfer)
                .amount(new BigDecimal("10000000.00"))
                .runningBalance(new BigDecimal("10000000.00"))
                .description("Other clan")
                .createdByAccountId("ACC_OTHER")
                .approvedByAccountId("ACC_OTHER")
                .recordHash("c".repeat(64))
                .createdAt(Instant.now())
                .build();

        ProposeLedgerAdjustmentRequest request = new ProposeLedgerAdjustmentRequest(
                TX_ID,
                new BigDecimal("1000000.00"),
                "Truy vấn chéo dòng họ",
                null
        );

        when(immutableLedgerRepository.findById(TX_ID)).thenReturn(Optional.of(otherClanLedger));

        assertThatThrownBy(() -> service.proposeAdjustment(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Từ chối với AccessDeniedException khi thành viên không có vai trò Treasurer / HeadOfOrganizer / ClanHead")
    void proposeAdjustment_unauthorizedMember_throwsAccessDenied() {
        ProposeLedgerAdjustmentRequest request = new ProposeLedgerAdjustmentRequest(
                TX_ID,
                new BigDecimal("-500000.00"),
                "Không đủ quyền",
                null
        );

        when(immutableLedgerRepository.findById(TX_ID)).thenReturn(Optional.of(originalLedger));
        when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
        when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(false);
        when(currentUserProvider.currentMemberId()).thenReturn(Optional.of("MEM_ORDINARY"));
        when(eventQueryService.getMemberRolesInEvent(EVENT_ID, "MEM_ORDINARY"))
                .thenReturn(Set.of(EventRole.OrganizerMember));

        assertThatThrownBy(() -> service.proposeAdjustment(request))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Tra cứu theo requestId và danh sách lọc theo bộ lọc dòng họ hoạt động chính xác")
    void getAndSearchAdjustments_success() {
        LedgerAdjustmentRequest entity = LedgerAdjustmentRequest.builder()
                .requestId("LAR_GET_01")
                .originalTransactionId(TX_ID)
                .proposedAdjustmentAmount(new BigDecimal("-2000000.00"))
                .adjustmentReason("Sai sót chứng từ")
                .proposedByAccountId(CALLER_ACCOUNT_ID)
                .approvalStatus(LedgerAdjustmentStatus.Pending)
                .createdAt(Instant.now())
                .build();

        when(ledgerAdjustmentRequestRepository.findById("LAR_GET_01")).thenReturn(Optional.of(entity));
        when(immutableLedgerRepository.findById(TX_ID)).thenReturn(Optional.of(originalLedger));

        LedgerAdjustmentRequestResponse single = service.getAdjustmentRequestById("LAR_GET_01");
        assertThat(single.requestId()).isEqualTo("LAR_GET_01");
        assertThat(single.originalTransactionAmount()).isEqualByComparingTo("50000000.00");

        when(ledgerAdjustmentRequestRepository.findByFilter(CLAN_ID, TX_ID, LedgerAdjustmentStatus.Pending))
                .thenReturn(List.of(entity));
        when(immutableLedgerRepository.findAllById(Set.of(TX_ID))).thenReturn(List.of(originalLedger));

        List<LedgerAdjustmentRequestResponse> list =
                service.searchAdjustmentRequests(TX_ID, LedgerAdjustmentStatus.Pending);
        assertThat(list).hasSize(1);
        assertThat(list.getFirst().requestId()).isEqualTo("LAR_GET_01");
    }

    @Test
    @DisplayName("US-59: Trưởng tộc phê duyệt bù trừ âm (-5tr) thành công -> INSERT AdjustmentTransaction, tính running_balance và SHA-256")
    void reviewAdjustment_approveNegative_appendsLedgerAndChainsHash() {
        Instant latestTs = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS);
        ImmutableLedger latestLedger = ImmutableLedger.builder()
                .transactionId("LDG_LATEST")
                .clanId(CLAN_ID)
                .eventId(EVENT_ID)
                .transactionType(LedgerTransactionType.SurplusTransfer)
                .amount(new BigDecimal("50000000.00"))
                .runningBalance(new BigDecimal("170000000.00"))
                .description("Kết dư đợt gần nhất")
                .createdByAccountId(CALLER_ACCOUNT_ID)
                .approvedByAccountId(CLAN_HEAD_ACCOUNT_ID)
                .recordHash("f".repeat(64))
                .createdAt(latestTs)
                .build();

        LedgerAdjustmentRequest pendingRequest = LedgerAdjustmentRequest.builder()
                .requestId("LAR_REV_01")
                .originalTransactionId(TX_ID)
                .proposedAdjustmentAmount(new BigDecimal("-5000000.00"))
                .adjustmentReason("Tính nhầm hóa đơn thuê rạp")
                .proposedByAccountId(CALLER_ACCOUNT_ID)
                .approvalStatus(LedgerAdjustmentStatus.Pending)
                .createdAt(Instant.now().minus(10, ChronoUnit.MINUTES))
                .build();

        ReviewLedgerAdjustmentRequest request = new ReviewLedgerAdjustmentRequest(
                "LAR_REV_01", true, null, "Bù trừ giảm chi phí thuê rạp theo biên bản"
        );

        when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
        when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(true);
        when(ledgerAdjustmentRequestRepository.findByIdForUpdate("LAR_REV_01"))
                .thenReturn(Optional.of(pendingRequest));
        when(immutableLedgerRepository.findById(TX_ID)).thenReturn(Optional.of(originalLedger));
        when(currentUserProvider.currentAccountId()).thenReturn(Optional.of(CLAN_HEAD_ACCOUNT_ID));
        when(immutableLedgerRepository.acquireClanLedgerLock(CLAN_ID)).thenReturn(1);
        when(immutableLedgerRepository.findFirstByClanIdOrderByCreatedAtDescTransactionIdDesc(CLAN_ID))
                .thenReturn(Optional.of(latestLedger));
        when(ledgerIdGenerator.generate()).thenReturn("LDG0001ADJ0001");
        when(immutableLedgerRepository.existsById("LDG0001ADJ0001")).thenReturn(false);
        when(immutableLedgerRepository.saveAndFlush(any(ImmutableLedger.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(ledgerAdjustmentRequestRepository.save(any(LedgerAdjustmentRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ReviewLedgerAdjustmentResponse response = service.reviewAdjustment(request);

        verify(immutableLedgerRepository).acquireClanLedgerLock(CLAN_ID);
        assertThat(response.adjustmentRequest().approvalStatus()).isEqualTo(LedgerAdjustmentStatus.Approved);
        assertThat(response.adjustmentRequest().reviewedByAccountId()).isEqualTo(CLAN_HEAD_ACCOUNT_ID);
        assertThat(response.adjustmentRequest().rejectionReason()).isNull();

        assertThat(response.ledgerEntry()).isNotNull();
        assertThat(response.ledgerEntry().transactionId()).isEqualTo("LDG0001ADJ0001");
        assertThat(response.ledgerEntry().transactionType()).isEqualTo(LedgerTransactionType.AdjustmentTransaction);
        assertThat(response.ledgerEntry().adjustmentRequestId()).isEqualTo("LAR_REV_01");
        assertThat(response.ledgerEntry().originalTransactionId()).isEqualTo(TX_ID);
        assertThat(response.ledgerEntry().amount()).isEqualByComparingTo("-5000000.00");
        assertThat(response.ledgerEntry().runningBalance()).isEqualByComparingTo("165000000.00");
        assertThat(response.ledgerEntry().createdByAccountId()).isEqualTo(CALLER_ACCOUNT_ID);
        assertThat(response.ledgerEntry().approvedByAccountId()).isEqualTo(CLAN_HEAD_ACCOUNT_ID);
        assertThat(response.ledgerEntry().previousHash()).isEqualTo(latestLedger.getRecordHash());
        assertThat(response.ledgerEntry().recordHash()).matches("^[0-9a-f]{64}$");

        ArgumentCaptor<ImmutableLedger> savedLedgerCaptor = ArgumentCaptor.forClass(ImmutableLedger.class);
        verify(immutableLedgerRepository).saveAndFlush(savedLedgerCaptor.capture());
        assertThat(ledgerHashCalculator.verifyRecordHash(savedLedgerCaptor.getValue(), latestLedger.getRecordHash()))
                .isTrue();

        ArgumentCaptor<NotificationCommand> notifCaptor = ArgumentCaptor.forClass(NotificationCommand.class);
        verify(notificationService).notify(notifCaptor.capture());
        assertThat(notifCaptor.getValue().recipientAccountId()).isEqualTo(CALLER_ACCOUNT_ID);
        assertThat(notifCaptor.getValue().category()).isEqualTo(NotificationCategory.Finance);
        assertThat(notifCaptor.getValue().referenceId()).isEqualTo("LAR_REV_01");
    }

    @Test
    @DisplayName("US-59: Trưởng tộc tự duyệt đề xuất dương (+) do chính mình lập thành công")
    void reviewAdjustment_approvePositive_selfApproval_success() {
        LedgerAdjustmentRequest selfProposal = LedgerAdjustmentRequest.builder()
                .requestId("LAR_SELF_01")
                .originalTransactionId(TX_ID)
                .proposedAdjustmentAmount(new BigDecimal("3250000.00"))
                .adjustmentReason("Bổ sung khoản hoàn cọc")
                .proposedByAccountId(CLAN_HEAD_ACCOUNT_ID)
                .approvalStatus(LedgerAdjustmentStatus.Pending)
                .createdAt(Instant.now().minus(5, ChronoUnit.MINUTES))
                .build();

        ReviewLedgerAdjustmentRequest request = new ReviewLedgerAdjustmentRequest(
                "LAR_SELF_01", true, null, null
        );

        when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
        when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(true);
        when(ledgerAdjustmentRequestRepository.findByIdForUpdate("LAR_SELF_01"))
                .thenReturn(Optional.of(selfProposal));
        when(immutableLedgerRepository.findById(TX_ID)).thenReturn(Optional.of(originalLedger));
        when(currentUserProvider.currentAccountId()).thenReturn(Optional.of(CLAN_HEAD_ACCOUNT_ID));
        when(immutableLedgerRepository.acquireClanLedgerLock(CLAN_ID)).thenReturn(1);
        when(immutableLedgerRepository.findFirstByClanIdOrderByCreatedAtDescTransactionIdDesc(CLAN_ID))
                .thenReturn(Optional.of(originalLedger));
        when(ledgerIdGenerator.generate()).thenReturn("LDG0002ADJ0002");
        when(immutableLedgerRepository.existsById("LDG0002ADJ0002")).thenReturn(false);
        when(immutableLedgerRepository.saveAndFlush(any(ImmutableLedger.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(ledgerAdjustmentRequestRepository.save(any(LedgerAdjustmentRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ReviewLedgerAdjustmentResponse response = service.reviewAdjustment(request);

        assertThat(response.adjustmentRequest().approvalStatus()).isEqualTo(LedgerAdjustmentStatus.Approved);
        assertThat(response.ledgerEntry()).isNotNull();
        assertThat(response.ledgerEntry().runningBalance()).isEqualByComparingTo("123250000.00");
        assertThat(response.ledgerEntry().description())
                .isEqualTo("Bù trừ giao dịch LDG_001 (LAR_SELF_01): Bổ sung khoản hoàn cọc");
    }

    @Test
    @DisplayName("US-59: Chặn phê duyệt (400 BusinessRuleException) nếu số dư Quỹ chung sau bù trừ bị âm")
    void reviewAdjustment_negativeNewBalance_throwsBusinessRuleAndDoesNotInsert() {
        LedgerAdjustmentRequest largeNegative = LedgerAdjustmentRequest.builder()
                .requestId("LAR_NEG_01")
                .originalTransactionId(TX_ID)
                .proposedAdjustmentAmount(new BigDecimal("-200000000.00"))
                .adjustmentReason("Vượt quá số dư quỹ hiện có")
                .proposedByAccountId(CALLER_ACCOUNT_ID)
                .approvalStatus(LedgerAdjustmentStatus.Pending)
                .createdAt(Instant.now())
                .build();

        when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(true);
        when(ledgerAdjustmentRequestRepository.findByIdForUpdate("LAR_NEG_01"))
                .thenReturn(Optional.of(largeNegative));
        when(immutableLedgerRepository.findById(TX_ID)).thenReturn(Optional.of(originalLedger));
        when(currentUserProvider.currentAccountId()).thenReturn(Optional.of("ACC_ADMIN"));
        when(immutableLedgerRepository.acquireClanLedgerLock(CLAN_ID)).thenReturn(1);
        when(immutableLedgerRepository.findFirstByClanIdOrderByCreatedAtDescTransactionIdDesc(CLAN_ID))
                .thenReturn(Optional.of(originalLedger));

        ReviewLedgerAdjustmentRequest request = new ReviewLedgerAdjustmentRequest(
                "LAR_NEG_01", true, null, null
        );

        assertThatThrownBy(() -> service.reviewAdjustment(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("âm");

        verify(immutableLedgerRepository, never()).saveAndFlush(any());
        verify(ledgerAdjustmentRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("US-59: Từ chối đề xuất kèm lý do -> cập nhật Rejected, lưu lý do, không ghi Sổ cái")
    void reviewAdjustment_rejectWithReason_updatesStatusWithoutLedgerInsert() {
        LedgerAdjustmentRequest pendingRequest = LedgerAdjustmentRequest.builder()
                .requestId("LAR_REJ_01")
                .originalTransactionId(TX_ID)
                .proposedAdjustmentAmount(new BigDecimal("-1500000.00"))
                .adjustmentReason("Đề xuất chưa rõ chứng từ")
                .proposedByAccountId(CALLER_ACCOUNT_ID)
                .approvalStatus(LedgerAdjustmentStatus.Pending)
                .createdAt(Instant.now())
                .build();

        when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
        when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(true);
        when(ledgerAdjustmentRequestRepository.findByIdForUpdate("LAR_REJ_01"))
                .thenReturn(Optional.of(pendingRequest));
        when(immutableLedgerRepository.findById(TX_ID)).thenReturn(Optional.of(originalLedger));
        when(currentUserProvider.currentAccountId()).thenReturn(Optional.of(CLAN_HEAD_ACCOUNT_ID));
        when(ledgerAdjustmentRequestRepository.save(any(LedgerAdjustmentRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ReviewLedgerAdjustmentRequest request = new ReviewLedgerAdjustmentRequest(
                "LAR_REJ_01", false, "Hóa đơn đính kèm bị mờ, cần đối chiếu lại bản gốc", null
        );

        ReviewLedgerAdjustmentResponse response = service.reviewAdjustment(request);

        assertThat(response.ledgerEntry()).isNull();
        assertThat(response.adjustmentRequest().approvalStatus()).isEqualTo(LedgerAdjustmentStatus.Rejected);
        assertThat(response.adjustmentRequest().reviewedByAccountId()).isEqualTo(CLAN_HEAD_ACCOUNT_ID);
        assertThat(response.adjustmentRequest().rejectionReason())
                .isEqualTo("Hóa đơn đính kèm bị mờ, cần đối chiếu lại bản gốc");
        assertThat(response.adjustmentRequest().reviewedAt()).isNotNull();

        verify(immutableLedgerRepository, never()).acquireClanLedgerLock(any());
        verify(immutableLedgerRepository, never()).saveAndFlush(any());
        verify(notificationService).notify(any(NotificationCommand.class));
    }

    @Test
    @DisplayName("US-59: Từ chối đề xuất mà thiếu rejectionReason -> ném BusinessRuleException")
    void reviewAdjustment_rejectWithoutReason_throwsBusinessRule() {
        when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
        when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(true);

        ReviewLedgerAdjustmentRequest request = new ReviewLedgerAdjustmentRequest(
                "LAR_REJ_02", false, "   ", null
        );

        assertThatThrownBy(() -> service.reviewAdjustment(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Lý do từ chối");
    }

    @Test
    @DisplayName("US-59: Duyệt đề xuất không ở trạng thái Pending (đã Approved/Rejected) -> ném BusinessRuleException")
    void reviewAdjustment_alreadyReviewed_throwsBusinessRule() {
        LedgerAdjustmentRequest alreadyApproved = LedgerAdjustmentRequest.builder()
                .requestId("LAR_DONE_01")
                .originalTransactionId(TX_ID)
                .proposedAdjustmentAmount(new BigDecimal("-1000000.00"))
                .adjustmentReason("Đã duyệt trước đó")
                .proposedByAccountId(CALLER_ACCOUNT_ID)
                .approvalStatus(LedgerAdjustmentStatus.Approved)
                .createdAt(Instant.now())
                .build();

        when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
        when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(true);
        when(ledgerAdjustmentRequestRepository.findByIdForUpdate("LAR_DONE_01"))
                .thenReturn(Optional.of(alreadyApproved));
        when(immutableLedgerRepository.findById(TX_ID)).thenReturn(Optional.of(originalLedger));

        ReviewLedgerAdjustmentRequest request = new ReviewLedgerAdjustmentRequest(
                "LAR_DONE_01", true, null, null
        );

        assertThatThrownBy(() -> service.reviewAdjustment(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Approved");
    }

    @Test
    @DisplayName("US-59: Tài khoản không phải Trưởng tộc hoặc Admin -> ném AccessDeniedException")
    void reviewAdjustment_unauthorizedCaller_throwsAccessDenied() {
        when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
        when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(false);

        ReviewLedgerAdjustmentRequest request = new ReviewLedgerAdjustmentRequest(
                "LAR_UNAUTH_01", true, null, null
        );

        assertThatThrownBy(() -> service.reviewAdjustment(request))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("US-59: Không tìm thấy requestId hoặc requestId thuộc dòng họ khác -> ném ResourceNotFoundException")
    void reviewAdjustment_missingOrCrossClan_throwsNotFound() {
        ImmutableLedger otherClanLedger = ImmutableLedger.builder()
                .transactionId(TX_ID)
                .clanId(OTHER_CLAN_ID)
                .transactionType(LedgerTransactionType.SurplusTransfer)
                .amount(new BigDecimal("10000000.00"))
                .runningBalance(new BigDecimal("10000000.00"))
                .description("Other")
                .createdByAccountId("ACC_OTHER")
                .approvedByAccountId("ACC_OTHER")
                .recordHash("e".repeat(64))
                .createdAt(Instant.now())
                .build();
        LedgerAdjustmentRequest crossClanReq = LedgerAdjustmentRequest.builder()
                .requestId("LAR_CROSS_01")
                .originalTransactionId(TX_ID)
                .proposedAdjustmentAmount(new BigDecimal("-500000.00"))
                .adjustmentReason("Cross clan")
                .proposedByAccountId("ACC_OTHER")
                .approvalStatus(LedgerAdjustmentStatus.Pending)
                .createdAt(Instant.now())
                .build();

        when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(true);
        when(ledgerAdjustmentRequestRepository.findByIdForUpdate("LAR_MISSING"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reviewAdjustment(
                new ReviewLedgerAdjustmentRequest("LAR_MISSING", true, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);

        when(ledgerAdjustmentRequestRepository.findByIdForUpdate("LAR_CROSS_01"))
                .thenReturn(Optional.of(crossClanReq));
        when(immutableLedgerRepository.findById(TX_ID)).thenReturn(Optional.of(otherClanLedger));

        assertThatThrownBy(() -> service.reviewAdjustment(
                new ReviewLedgerAdjustmentRequest("LAR_CROSS_01", true, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("US-59: Lỗi gửi thông báo không làm rollback giao dịch phê duyệt")
    void reviewAdjustment_notificationFailure_doesNotRollback() {
        LedgerAdjustmentRequest pendingRequest = LedgerAdjustmentRequest.builder()
                .requestId("LAR_NOTIF_ERR")
                .originalTransactionId(TX_ID)
                .proposedAdjustmentAmount(new BigDecimal("1000000.00"))
                .adjustmentReason("Bổ sung khoản thu")
                .proposedByAccountId(CALLER_ACCOUNT_ID)
                .approvalStatus(LedgerAdjustmentStatus.Pending)
                .createdAt(Instant.now())
                .build();

        when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
        when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(true);
        when(ledgerAdjustmentRequestRepository.findByIdForUpdate("LAR_NOTIF_ERR"))
                .thenReturn(Optional.of(pendingRequest));
        when(immutableLedgerRepository.findById(TX_ID)).thenReturn(Optional.of(originalLedger));
        when(currentUserProvider.currentAccountId()).thenReturn(Optional.of(CLAN_HEAD_ACCOUNT_ID));
        when(immutableLedgerRepository.acquireClanLedgerLock(CLAN_ID)).thenReturn(1);
        when(immutableLedgerRepository.findFirstByClanIdOrderByCreatedAtDescTransactionIdDesc(CLAN_ID))
                .thenReturn(Optional.of(originalLedger));
        when(ledgerIdGenerator.generate()).thenReturn("LDG0003NOTIF");
        when(immutableLedgerRepository.existsById("LDG0003NOTIF")).thenReturn(false);
        when(immutableLedgerRepository.saveAndFlush(any(ImmutableLedger.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(ledgerAdjustmentRequestRepository.save(any(LedgerAdjustmentRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("Notification service unavailable"))
                .when(notificationService).notify(any(NotificationCommand.class));

        ReviewLedgerAdjustmentResponse response = service.reviewAdjustment(
                new ReviewLedgerAdjustmentRequest("LAR_NOTIF_ERR", true, null, null)
        );

        assertThat(response.adjustmentRequest().approvalStatus()).isEqualTo(LedgerAdjustmentStatus.Approved);
        assertThat(response.ledgerEntry().transactionId()).isEqualTo("LDG0003NOTIF");
    }
}
