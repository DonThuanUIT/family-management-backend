package com.familyfund.finance.internal.service;

import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.core.exception.DuplicateResourceException;
import com.familyfund.core.exception.LedgerImmutabilityViolationException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.core.security.SystemRoles;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.finance.api.dto.CommonFundDisbursementStatus;
import com.familyfund.finance.api.dto.LedgerAdjustmentStatus;
import com.familyfund.finance.api.dto.LedgerAnnualAuditStatus;
import com.familyfund.finance.api.dto.LedgerTransactionType;
import com.familyfund.finance.internal.dto.request.AnnualLedgerAuditRequest;
import com.familyfund.finance.internal.dto.request.ProposeLedgerAdjustmentRequest;
import com.familyfund.finance.internal.dto.response.LedgerAdjustmentRequestResponse;
import com.familyfund.finance.internal.dto.response.LedgerAnnualAuditResponse;
import com.familyfund.finance.internal.entity.ImmutableLedger;
import com.familyfund.finance.internal.entity.LedgerAdjustmentRequest;
import com.familyfund.finance.internal.entity.LedgerAnnualAudit;
import com.familyfund.finance.internal.repository.CommonFundDisbursementRequestRepository;
import com.familyfund.finance.internal.repository.ImmutableLedgerRepository;
import com.familyfund.finance.internal.repository.LedgerAdjustmentRequestRepository;
import com.familyfund.finance.internal.repository.LedgerAnnualAuditRepository;
import com.familyfund.finance.internal.util.ImmutableLedgerHashCalculator;
import com.familyfund.finance.internal.util.ImmutableLedgerIdGenerator;
import com.familyfund.finance.internal.util.LedgerAnnualAuditIdGenerator;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.identity.api.IdentityRoleCommandService;
import com.familyfund.identity.api.dto.AccountSummaryDto;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LedgerAnnualAuditServiceImplTest {

    private static final String CLAN_ID = "CLN_TEST";
    private static final String ACC_HEAD = "ACC_CLAN_HEAD";
    private static final String TX_BASE = "LDG_BASE_01";
    private static final String TX_OLDER = "LDG_OLD_01";
    private static final String AUDIT_ID = "AUD0001UNIT00001";
    private static final String CLOSING_TX_ID = "LDG0001CLOSE0001";

    @Mock
    private LedgerAnnualAuditRepository ledgerAnnualAuditRepository;

    @Mock
    private ImmutableLedgerRepository immutableLedgerRepository;

    @Mock
    private LedgerAdjustmentRequestRepository ledgerAdjustmentRequestRepository;

    @Mock
    private CommonFundDisbursementRequestRepository commonFundDisbursementRequestRepository;

    @Mock
    private LedgerAdjustmentService ledgerAdjustmentService;

    @Mock
    private LedgerAnnualAuditIdGenerator auditIdGenerator;

    @Mock
    private ImmutableLedgerIdGenerator ledgerIdGenerator;

    @Spy
    private ImmutableLedgerHashCalculator ledgerHashCalculator = new ImmutableLedgerHashCalculator();

    @Mock
    private GenealogyQueryService genealogyQueryService;

    @Mock
    private IdentityRoleCommandService identityRoleCommandService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private LedgerAnnualAuditServiceImpl service;

    private ImmutableLedger latestLedger;

    @BeforeEach
    void setUp() {
        TenantContext.setClanId(CLAN_ID);
        lenient().when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(true);
        lenient().when(currentUserProvider.currentAccountId()).thenReturn(Optional.of(ACC_HEAD));
        lenient().when(genealogyQueryService.getClanName(CLAN_ID)).thenReturn(Optional.of("Dòng họ Test"));
        lenient().when(ledgerAnnualAuditRepository.existsById(any())).thenReturn(false);
        lenient().when(immutableLedgerRepository.existsById(any())).thenReturn(false);
        lenient().when(ledgerAdjustmentRequestRepository.countPendingByClanId(CLAN_ID)).thenReturn(0L);
        lenient().when(commonFundDisbursementRequestRepository.countByClanIdAndApprovalStatus(
                CLAN_ID, CommonFundDisbursementStatus.Pending)).thenReturn(0L);
        lenient().when(identityRoleCommandService.findAccountDisplayNames(any()))
                .thenReturn(Map.of(ACC_HEAD, "Cụ Trưởng Tộc"));

        Instant baseTime = Instant.parse("2026-06-01T10:00:00Z");
        String hash = ledgerHashCalculator.computeRecordHash(
                ImmutableLedgerHashCalculator.GENESIS_HASH,
                TX_BASE, CLAN_ID, "EVT_01", null, null, null,
                LedgerTransactionType.SurplusTransfer,
                new BigDecimal("100000000.00"),
                new BigDecimal("100000000.00"),
                "Kết dư đại lễ 2026",
                ACC_HEAD,
                ACC_HEAD,
                baseTime
        );
        latestLedger = ImmutableLedger.builder()
                .transactionId(TX_BASE)
                .clanId(CLAN_ID)
                .eventId("EVT_01")
                .transactionType(LedgerTransactionType.SurplusTransfer)
                .amount(new BigDecimal("100000000.00"))
                .runningBalance(new BigDecimal("100000000.00"))
                .description("Kết dư đại lễ 2026")
                .createdByAccountId(ACC_HEAD)
                .approvedByAccountId(ACC_HEAD)
                .previousHash(ImmutableLedgerHashCalculator.GENESIS_HASH)
                .recordHash(hash)
                .createdAt(baseTime)
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Matched audit on non-empty ledger seals OpeningBalanceCarryForward with zero cashflow and SHA-256")
    void performAnnualAudit_matchedOnNonEmptyLedger_sealsCarryForwardAndNotifies() {
        when(ledgerAnnualAuditRepository.existsByClanIdAndAuditYearAndAuditStatus(
                CLAN_ID, 2026, LedgerAnnualAuditStatus.Matched)).thenReturn(false);
        when(immutableLedgerRepository.findByClanIdOrderByCreatedAtAscTransactionIdAsc(CLAN_ID))
                .thenReturn(List.of(latestLedger));
        when(auditIdGenerator.generate()).thenReturn(AUDIT_ID);
        when(ledgerIdGenerator.generate()).thenReturn(CLOSING_TX_ID);
        when(immutableLedgerRepository.saveAndFlush(any(ImmutableLedger.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(ledgerAnnualAuditRepository.saveAndFlush(any(LedgerAnnualAudit.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(identityRoleCommandService.findActiveAccountsByClan(CLAN_ID))
                .thenReturn(List.of(new AccountSummaryDto(ACC_HEAD, "MEM_HEAD", "0900", "head@example.com", "Active", Set.of(SystemRoles.CLAN_HEAD))));

        AnnualLedgerAuditRequest request = new AnnualLedgerAuditRequest(
                new BigDecimal("100000000.00"),
                2026,
                null,
                "Khớp tiền mặt tại két",
                "https://cdn.example/audit2026.pdf"
        );

        LedgerAnnualAuditResponse response = service.performAnnualAudit(request);

        assertThat(response.auditId()).isEqualTo(AUDIT_ID);
        assertThat(response.auditYear()).isEqualTo(2026);
        assertThat(response.nextPeriodYear()).isEqualTo(2027);
        assertThat(response.auditStatus()).isEqualTo(LedgerAnnualAuditStatus.Matched);
        assertThat(response.systemBalance()).isEqualByComparingTo("100000000.00");
        assertThat(response.actualBalance()).isEqualByComparingTo("100000000.00");
        assertThat(response.discrepancyAmount()).isEqualByComparingTo("0.00");
        assertThat(response.openingBalanceNextPeriod()).isEqualByComparingTo("100000000.00");
        assertThat(response.closingTransactionId()).isEqualTo(CLOSING_TX_ID);
        assertThat(response.adjustmentRequestId()).isNull();
        assertThat(response.autoGeneratedAdjustmentRequest()).isNull();
        assertThat(response.auditorDisplayName()).isEqualTo("Cụ Trưởng Tộc");

        ArgumentCaptor<ImmutableLedger> ledgerCaptor = ArgumentCaptor.forClass(ImmutableLedger.class);
        verify(immutableLedgerRepository).saveAndFlush(ledgerCaptor.capture());
        ImmutableLedger savedEntry = ledgerCaptor.getValue();
        assertThat(savedEntry.getTransactionType()).isEqualTo(LedgerTransactionType.OpeningBalanceCarryForward);
        assertThat(savedEntry.getAmount()).isEqualByComparingTo("0.00");
        assertThat(savedEntry.getRunningBalance()).isEqualByComparingTo("100000000.00");
        assertThat(savedEntry.getOriginalTransactionId()).isNull();
        assertThat(savedEntry.getPreviousHash()).isEqualTo(latestLedger.getRecordHash());
        assertThat(ledgerHashCalculator.verifyRecordHash(savedEntry, latestLedger.getRecordHash())).isTrue();

        verify(ledgerAdjustmentService, never()).proposeAdjustment(any());
        verify(notificationService).notify(any(NotificationCommand.class));
    }

    @Test
    @DisplayName("Matched audit on empty ledger (0 == 0) creates GENESIS OpeningBalanceCarryForward entry")
    void performAnnualAudit_matchedOnEmptyLedger_createsGenesisEntry() {
        when(ledgerAnnualAuditRepository.existsByClanIdAndAuditYearAndAuditStatus(
                CLAN_ID, 2026, LedgerAnnualAuditStatus.Matched)).thenReturn(false);
        when(immutableLedgerRepository.findByClanIdOrderByCreatedAtAscTransactionIdAsc(CLAN_ID))
                .thenReturn(List.of());
        when(auditIdGenerator.generate()).thenReturn(AUDIT_ID);
        when(ledgerIdGenerator.generate()).thenReturn(CLOSING_TX_ID);
        when(immutableLedgerRepository.saveAndFlush(any(ImmutableLedger.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(ledgerAnnualAuditRepository.saveAndFlush(any(LedgerAnnualAudit.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        LedgerAnnualAuditResponse response = service.performAnnualAudit(
                new AnnualLedgerAuditRequest(BigDecimal.ZERO, 2026, null, null, null));

        assertThat(response.auditStatus()).isEqualTo(LedgerAnnualAuditStatus.Matched);
        assertThat(response.closingLedgerEntry()).isNotNull();
        assertThat(response.closingLedgerEntry().previousHash()).isEqualTo(ImmutableLedgerHashCalculator.GENESIS_HASH);
        assertThat(response.closingLedgerEntry().transactionType()).isEqualTo(LedgerTransactionType.OpeningBalanceCarryForward);
        assertThat(response.closingLedgerEntry().amount()).isEqualByComparingTo("0.00");
        assertThat(response.openingBalanceNextPeriod()).isEqualByComparingTo("0.00");
        assertThat(response.referenceTransactionId()).isNull();
    }

    @Test
    @DisplayName("Negative and positive discrepancies auto-generate signed US-15 adjustment requests")
    void performAnnualAudit_discrepancies_autoGenerateSignedUs15Requests() {
        when(ledgerAnnualAuditRepository.existsByClanIdAndAuditYearAndAuditStatus(
                CLAN_ID, 2026, LedgerAnnualAuditStatus.Matched)).thenReturn(false);
        when(immutableLedgerRepository.findByClanIdOrderByCreatedAtAscTransactionIdAsc(CLAN_ID))
                .thenReturn(List.of(latestLedger));
        when(auditIdGenerator.generate()).thenReturn(AUDIT_ID);
        when(ledgerAnnualAuditRepository.saveAndFlush(any(LedgerAnnualAudit.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        LedgerAdjustmentRequestResponse adjResp = new LedgerAdjustmentRequestResponse(
                "LAR0001TEST", TX_BASE, CLAN_ID, "EVT_01", LedgerTransactionType.SurplusTransfer,
                new BigDecimal("100000000.00"), new BigDecimal("-5000000.00"),
                "Reason", null, ACC_HEAD, LedgerAdjustmentStatus.Pending, null, null,
                Instant.now(), null
        );
        when(ledgerAdjustmentService.proposeAdjustment(any(ProposeLedgerAdjustmentRequest.class)))
                .thenReturn(adjResp);

        // Deficit (-5,000,000)
        LedgerAnnualAuditResponse deficitResp = service.performAnnualAudit(
                new AnnualLedgerAuditRequest(new BigDecimal("95000000.00"), 2026, null, "Thiếu quỹ", "https://cdn/doc.pdf"));

        assertThat(deficitResp.auditStatus()).isEqualTo(LedgerAnnualAuditStatus.DiscrepancyDetected);
        assertThat(deficitResp.discrepancyAmount()).isEqualByComparingTo("-5000000.00");
        assertThat(deficitResp.adjustmentRequestId()).isEqualTo("LAR0001TEST");
        assertThat(deficitResp.closingTransactionId()).isNull();
        assertThat(deficitResp.openingBalanceNextPeriod()).isNull();

        ArgumentCaptor<ProposeLedgerAdjustmentRequest> proposeCaptor =
                ArgumentCaptor.forClass(ProposeLedgerAdjustmentRequest.class);
        verify(ledgerAdjustmentService).proposeAdjustment(proposeCaptor.capture());
        assertThat(proposeCaptor.getValue().originalTransactionId()).isEqualTo(TX_BASE);
        assertThat(proposeCaptor.getValue().proposedAdjustmentAmount()).isEqualByComparingTo("-5000000.00");
        assertThat(proposeCaptor.getValue().adjustmentReason()).contains(AUDIT_ID).contains("-5000000.00");
        assertThat(proposeCaptor.getValue().evidenceAttachmentUrl()).isEqualTo("https://cdn/doc.pdf");

        // Surplus (+3,000,000) with explicit referenceTransactionId
        when(immutableLedgerRepository.findByTransactionIdAndClanId(TX_OLDER, CLAN_ID))
                .thenReturn(Optional.of(latestLedger));
        LedgerAdjustmentRequestResponse surplusAdjResp = new LedgerAdjustmentRequestResponse(
                "LAR0002TEST", TX_OLDER, CLAN_ID, null, LedgerTransactionType.SurplusTransfer,
                new BigDecimal("100000000.00"), new BigDecimal("3000000.00"),
                "Reason", null, ACC_HEAD, LedgerAdjustmentStatus.Pending, null, null,
                Instant.now(), null
        );
        when(ledgerAdjustmentService.proposeAdjustment(any(ProposeLedgerAdjustmentRequest.class)))
                .thenReturn(surplusAdjResp);

        LedgerAnnualAuditResponse surplusResp = service.performAnnualAudit(
                new AnnualLedgerAuditRequest(new BigDecimal("103000000.00"), 2026, TX_OLDER, null, null));

        assertThat(surplusResp.auditStatus()).isEqualTo(LedgerAnnualAuditStatus.DiscrepancyDetected);
        assertThat(surplusResp.discrepancyAmount()).isEqualByComparingTo("3000000.00");
        assertThat(surplusResp.referenceTransactionId()).isEqualTo(TX_OLDER);
        assertThat(surplusResp.adjustmentRequestId()).isEqualTo("LAR0002TEST");
    }

    @Test
    @DisplayName("Discrepancy on empty ledger or invalid referenceTransactionId throws clear exceptions")
    void performAnnualAudit_discrepancyEmptyLedgerOrMissingRef_throwsExceptions() {
        when(ledgerAnnualAuditRepository.existsByClanIdAndAuditYearAndAuditStatus(
                CLAN_ID, 2026, LedgerAnnualAuditStatus.Matched)).thenReturn(false);
        when(immutableLedgerRepository.findByClanIdOrderByCreatedAtAscTransactionIdAsc(CLAN_ID))
                .thenReturn(List.of());
        when(auditIdGenerator.generate()).thenReturn(AUDIT_ID);

        assertThatThrownBy(() -> service.performAnnualAudit(
                new AnnualLedgerAuditRequest(new BigDecimal("5000000.00"), 2026, null, null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("chưa có bút toán gốc nào");

        when(immutableLedgerRepository.findByTransactionIdAndClanId("LDG_OTHER", CLAN_ID))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.performAnnualAudit(
                new AnnualLedgerAuditRequest(new BigDecimal("5000000.00"), 2026, "LDG_OTHER", null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Rejects duplicate Matched year, broken SHA-256 chain, and pending US-15/US-60 requests")
    void performAnnualAudit_guards_blockInvalidPreconditions() {
        // 1. Duplicate matched year -> 409 DuplicateResourceException
        when(ledgerAnnualAuditRepository.existsByClanIdAndAuditYearAndAuditStatus(
                CLAN_ID, 2026, LedgerAnnualAuditStatus.Matched)).thenReturn(true);
        assertThatThrownBy(() -> service.performAnnualAudit(
                new AnnualLedgerAuditRequest(new BigDecimal("100000000.00"), 2026, null, null, null)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("2026");

        // 2. Broken SHA-256 chain -> 403 LedgerImmutabilityViolationException
        when(ledgerAnnualAuditRepository.existsByClanIdAndAuditYearAndAuditStatus(
                CLAN_ID, 2026, LedgerAnnualAuditStatus.Matched)).thenReturn(false);
        ImmutableLedger tampered = ImmutableLedger.builder()
                .transactionId(TX_BASE)
                .clanId(CLAN_ID)
                .transactionType(LedgerTransactionType.SurplusTransfer)
                .amount(new BigDecimal("100000000.00"))
                .runningBalance(new BigDecimal("100000000.00"))
                .description("Tampered")
                .createdByAccountId(ACC_HEAD)
                .approvedByAccountId(ACC_HEAD)
                .previousHash(ImmutableLedgerHashCalculator.GENESIS_HASH)
                .recordHash("f".repeat(64))
                .createdAt(Instant.now().truncatedTo(ChronoUnit.MICROS))
                .build();
        when(immutableLedgerRepository.findByClanIdOrderByCreatedAtAscTransactionIdAsc(CLAN_ID))
                .thenReturn(List.of(tampered));

        assertThatThrownBy(() -> service.performAnnualAudit(
                new AnnualLedgerAuditRequest(new BigDecimal("100000000.00"), 2026, null, null, null)))
                .isInstanceOf(LedgerImmutabilityViolationException.class)
                .hasMessageContaining("SHA-256");

        // 3. Pending adjustment request -> 400 BusinessRuleException
        when(immutableLedgerRepository.findByClanIdOrderByCreatedAtAscTransactionIdAsc(CLAN_ID))
                .thenReturn(List.of(latestLedger));
        when(ledgerAdjustmentRequestRepository.countPendingByClanId(CLAN_ID)).thenReturn(1L);
        assertThatThrownBy(() -> service.performAnnualAudit(
                new AnnualLedgerAuditRequest(new BigDecimal("100000000.00"), 2026, null, null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("US-15");

        // 4. Pending disbursement request -> 400 BusinessRuleException
        when(ledgerAdjustmentRequestRepository.countPendingByClanId(CLAN_ID)).thenReturn(0L);
        when(commonFundDisbursementRequestRepository.countByClanIdAndApprovalStatus(
                CLAN_ID, CommonFundDisbursementStatus.Pending)).thenReturn(2L);
        assertThatThrownBy(() -> service.performAnnualAudit(
                new AnnualLedgerAuditRequest(new BigDecimal("100000000.00"), 2026, null, null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("US-60");
    }

    @Test
    @DisplayName("Rejects unauthorized callers and missing tenant context")
    void performAnnualAudit_securityAndTenantGuards() {
        when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
        when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(false);

        assertThatThrownBy(() -> service.performAnnualAudit(
                new AnnualLedgerAuditRequest(BigDecimal.TEN, 2026, null, null, null)))
                .isInstanceOf(AccessDeniedException.class);

        try (org.mockito.MockedStatic<TenantContext> mockedTenant = org.mockito.Mockito.mockStatic(TenantContext.class)) {
            mockedTenant.when(TenantContext::getClanId).thenReturn("   ");
            assertThatThrownBy(() -> service.performAnnualAudit(
                    new AnnualLedgerAuditRequest(BigDecimal.TEN, 2026, null, null, null)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("clanId");
        }

        when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(true);
        when(genealogyQueryService.getClanName(CLAN_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.performAnnualAudit(
                new AnnualLedgerAuditRequest(BigDecimal.TEN, 2026, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(CLAN_ID);
    }

    @Test
    @DisplayName("searchAnnualAudits and getAnnualAuditById enrich linked adjustment and closing entry without N+1")
    void searchAndGetById_enrichesLinkedObjectsAndEnforcesTenant() {
        LedgerAnnualAudit matchedAudit = LedgerAnnualAudit.builder()
                .auditId("AUD_MATCH_01")
                .clanId(CLAN_ID)
                .auditYear(2026)
                .nextPeriodYear(2027)
                .auditDate(Instant.parse("2026-12-31T10:00:00Z"))
                .systemBalance(new BigDecimal("100000000.00"))
                .actualBalance(new BigDecimal("100000000.00"))
                .discrepancyAmount(BigDecimal.ZERO.setScale(2))
                .auditStatus(LedgerAnnualAuditStatus.Matched)
                .referenceTransactionId(TX_BASE)
                .closingTransactionId(CLOSING_TX_ID)
                .openingBalanceNextPeriod(new BigDecimal("100000000.00"))
                .auditorAccountId(ACC_HEAD)
                .createdAt(Instant.parse("2026-12-31T10:00:00Z"))
                .build();

        LedgerAnnualAudit discAudit = LedgerAnnualAudit.builder()
                .auditId("AUD_DISC_01")
                .clanId(CLAN_ID)
                .auditYear(2026)
                .nextPeriodYear(2027)
                .auditDate(Instant.parse("2026-12-30T10:00:00Z"))
                .systemBalance(new BigDecimal("100000000.00"))
                .actualBalance(new BigDecimal("95000000.00"))
                .discrepancyAmount(new BigDecimal("-5000000.00"))
                .auditStatus(LedgerAnnualAuditStatus.DiscrepancyDetected)
                .referenceTransactionId(TX_BASE)
                .adjustmentRequestId("LAR_01")
                .auditorAccountId(ACC_HEAD)
                .createdAt(Instant.parse("2026-12-30T10:00:00Z"))
                .build();

        LedgerAdjustmentRequest adjReq = LedgerAdjustmentRequest.builder()
                .requestId("LAR_01")
                .originalTransactionId(TX_BASE)
                .proposedAdjustmentAmount(new BigDecimal("-5000000.00"))
                .adjustmentReason("Lệch kiểm toán")
                .proposedByAccountId(ACC_HEAD)
                .approvalStatus(LedgerAdjustmentStatus.Approved)
                .createdAt(Instant.parse("2026-12-30T10:00:00Z"))
                .build();

        ImmutableLedger closingEntry = ImmutableLedger.builder()
                .transactionId(CLOSING_TX_ID)
                .clanId(CLAN_ID)
                .transactionType(LedgerTransactionType.OpeningBalanceCarryForward)
                .amount(new BigDecimal("0.00"))
                .runningBalance(new BigDecimal("100000000.00"))
                .description("Chốt sổ 2026")
                .createdByAccountId(ACC_HEAD)
                .approvedByAccountId(ACC_HEAD)
                .previousHash(latestLedger.getRecordHash())
                .recordHash("b".repeat(64))
                .createdAt(Instant.parse("2026-12-31T10:00:00Z"))
                .build();

        when(ledgerAnnualAuditRepository.findByFilter(CLAN_ID, 2026, null))
                .thenReturn(List.of(matchedAudit, discAudit));
        when(ledgerAdjustmentRequestRepository.findAllById(Set.of("LAR_01")))
                .thenReturn(List.of(adjReq));
        when(immutableLedgerRepository.findByClanIdAndTransactionIdIn(CLAN_ID, Set.of(CLOSING_TX_ID, TX_BASE)))
                .thenReturn(List.of(closingEntry, latestLedger));

        List<LedgerAnnualAuditResponse> results = service.searchAnnualAudits(2026, null);
        assertThat(results).hasSize(2);
        assertThat(results.get(0).closingLedgerEntry()).isNotNull();
        assertThat(results.get(0).closingLedgerEntry().transactionId()).isEqualTo(CLOSING_TX_ID);
        assertThat(results.get(1).autoGeneratedAdjustmentRequest()).isNotNull();
        assertThat(results.get(1).autoGeneratedAdjustmentRequest().requestId()).isEqualTo("LAR_01");

        when(ledgerAnnualAuditRepository.findByAuditIdAndClanId("AUD_MISSING", CLAN_ID))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getAnnualAuditById("AUD_MISSING"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
