package com.familyfund.finance.internal.service;

import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.event.api.EventQueryService;
import com.familyfund.finance.api.dto.CommonFundDisbursementStatus;
import com.familyfund.finance.api.dto.CommonFundExpenseCategory;
import com.familyfund.finance.api.dto.LedgerAdjustmentStatus;
import com.familyfund.finance.api.dto.LedgerTransactionType;
import com.familyfund.finance.internal.dto.response.LedgerHistoryItemResponse;
import com.familyfund.finance.internal.dto.response.LedgerHistoryPageResponse;
import com.familyfund.finance.internal.entity.CommonFundDisbursementRequest;
import com.familyfund.finance.internal.entity.ImmutableLedger;
import com.familyfund.finance.internal.entity.LedgerAdjustmentRequest;
import com.familyfund.finance.internal.repository.CommonFundDisbursementRequestRepository;
import com.familyfund.finance.internal.repository.ImmutableLedgerRepository;
import com.familyfund.finance.internal.repository.LedgerAdjustmentRequestRepository;
import com.familyfund.finance.internal.repository.LedgerFilteredAggregates;
import com.familyfund.finance.internal.repository.LedgerSearchCriteria;
import com.familyfund.identity.api.IdentityRoleCommandService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImmutableLedgerQueryServiceImplTest {

    private static final String CLAN_ID = "CLN_US62";
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Mock
    private ImmutableLedgerRepository immutableLedgerRepository;

    @Mock
    private LedgerAdjustmentRequestRepository ledgerAdjustmentRequestRepository;

    @Mock
    private CommonFundDisbursementRequestRepository commonFundDisbursementRequestRepository;

    @Mock
    private IdentityRoleCommandService identityRoleCommandService;

    @Mock
    private EventQueryService eventQueryService;

    @InjectMocks
    private ImmutableLedgerQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.setClanId(CLAN_ID);
        lenient().when(identityRoleCommandService.findAccountDisplayNames(anyCollection())).thenReturn(Map.of());
        lenient().when(eventQueryService.findEventNamesByIds(anyCollection())).thenReturn(Map.of());
        lenient().when(ledgerAdjustmentRequestRepository.findAllById(any())).thenReturn(List.of());
        lenient().when(commonFundDisbursementRequestRepository.findAllById(any())).thenReturn(List.of());
        lenient().when(ledgerAdjustmentRequestRepository.findByOriginalTransactionIdInAndApprovalStatus(
                anyCollection(), eq(LedgerAdjustmentStatus.Pending))).thenReturn(List.of());
        lenient().when(immutableLedgerRepository.findByClanIdAndOriginalTransactionIdInOrderByCreatedAtAscTransactionIdAsc(
                eq(CLAN_ID), anyCollection())).thenReturn(List.of());
        lenient().when(immutableLedgerRepository.findByClanIdAndTransactionIdIn(
                eq(CLAN_ID), anyCollection())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Lọc theo khoảng ngày yyyy-MM-dd (múi giờ Việt Nam +07:00), loại giao dịch, eventId, keyword, onlyWithAdjustments, sortDirection ASC/DESC và phân trang")
    void searchLedgerHistory_filtersDateRangeTypesKeywordAndSummaryMetrics() {
        Instant expectedFrom = LocalDate.of(2026, 11, 1).atStartOfDay(VN_ZONE).toInstant();
        Instant expectedTo = LocalDate.of(2026, 11, 30).atTime(23, 59, 59, 999_999_000).atZone(VN_ZONE).toInstant();

        ImmutableLedger latestEntry = ImmutableLedger.builder()
                .transactionId("LDG_LATEST")
                .clanId(CLAN_ID)
                .transactionType(LedgerTransactionType.CommonFundDisbursement)
                .amount(new BigDecimal("-20000000.00"))
                .runningBalance(new BigDecimal("77000000.00"))
                .description("Chi tu sửa")
                .createdByAccountId("ACC_TR")
                .approvedByAccountId("ACC_HEAD")
                .previousHash("a".repeat(64))
                .recordHash("b".repeat(64))
                .createdAt(Instant.parse("2026-11-25T10:00:00Z"))
                .build();

        ImmutableLedger beforePeriodEntry = ImmutableLedger.builder()
                .transactionId("LDG_OCT")
                .clanId(CLAN_ID)
                .runningBalance(new BigDecimal("50000000.00"))
                .createdAt(Instant.parse("2026-10-20T10:00:00Z"))
                .build();

        ImmutableLedger endOfPeriodEntry = ImmutableLedger.builder()
                .transactionId("LDG_NOV_END")
                .clanId(CLAN_ID)
                .runningBalance(new BigDecimal("77000000.00"))
                .createdAt(Instant.parse("2026-11-25T10:00:00Z"))
                .build();

        ImmutableLedger disbursementEntry = ImmutableLedger.builder()
                .transactionId("LDG_DISB_01")
                .clanId(CLAN_ID)
                .disbursementRequestId("CFD_01")
                .transactionType(LedgerTransactionType.CommonFundDisbursement)
                .amount(new BigDecimal("-20000000.00"))
                .runningBalance(new BigDecimal("77000000.00"))
                .description("Chi tu sửa Từ đường")
                .createdByAccountId("ACC_TR")
                .approvedByAccountId("ACC_HEAD")
                .previousHash("1".repeat(64))
                .recordHash("2".repeat(64))
                .createdAt(Instant.parse("2026-11-15T08:00:00Z"))
                .build();

        when(immutableLedgerRepository.searchLedgerEntries(any(LedgerSearchCriteria.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(disbursementEntry)));
        when(immutableLedgerRepository.aggregateFilteredFlow(any(LedgerSearchCriteria.class)))
                .thenReturn(new LedgerFilteredAggregates(
                        1L,
                        new BigDecimal("0.00"),
                        new BigDecimal("20000000.00"),
                        new BigDecimal("-20000000.00")
                ));
        when(immutableLedgerRepository.findFirstByClanIdOrderByCreatedAtDescTransactionIdDesc(CLAN_ID))
                .thenReturn(Optional.of(latestEntry));
        when(immutableLedgerRepository.findFirstByClanIdAndCreatedAtLessThanOrderByCreatedAtDescTransactionIdDesc(CLAN_ID, expectedFrom))
                .thenReturn(Optional.of(beforePeriodEntry));
        when(immutableLedgerRepository.findFirstByClanIdAndCreatedAtLessThanEqualOrderByCreatedAtDescTransactionIdDesc(CLAN_ID, expectedTo))
                .thenReturn(Optional.of(endOfPeriodEntry));
        when(commonFundDisbursementRequestRepository.findAllById(any()))
                .thenReturn(List.of(CommonFundDisbursementRequest.builder()
                        .requestId("CFD_01")
                        .clanId(CLAN_ID)
                        .title("Tu sửa mái Từ đường")
                        .category(CommonFundExpenseCategory.TempleMaintenance)
                        .amount(new BigDecimal("20000000.00"))
                        .purpose("Sửa ngói")
                        .proposedByAccountId("ACC_TR")
                        .approvalStatus(CommonFundDisbursementStatus.Approved)
                        .build()));
        when(identityRoleCommandService.findAccountDisplayNames(anyCollection()))
                .thenReturn(Map.of("ACC_TR", "Thủ Quỹ A", "ACC_HEAD", "Trưởng Tộc B"));

        LedgerHistoryPageResponse response = service.searchLedgerHistory(
                "2026-11-01",
                "2026-11-30",
                List.of(LedgerTransactionType.CommonFundDisbursement),
                "EVT_01",
                "Từ đường",
                false,
                "ASC",
                0,
                10
        );

        ArgumentCaptor<LedgerSearchCriteria> criteriaCaptor = ArgumentCaptor.forClass(LedgerSearchCriteria.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(immutableLedgerRepository).searchLedgerEntries(criteriaCaptor.capture(), pageableCaptor.capture());

        LedgerSearchCriteria criteria = criteriaCaptor.getValue();
        assertThat(criteria.clanId()).isEqualTo(CLAN_ID);
        assertThat(criteria.fromInstant()).isEqualTo(expectedFrom);
        assertThat(criteria.toInstant()).isEqualTo(expectedTo);
        assertThat(criteria.transactionTypes()).containsExactly(LedgerTransactionType.CommonFundDisbursement);
        assertThat(criteria.eventId()).isEqualTo("EVT_01");
        assertThat(criteria.keyword()).isEqualTo("Từ đường");
        assertThat(criteria.onlyWithAdjustments()).isFalse();

        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(10);
        assertThat(pageable.getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.ASC);

        assertThat(response.clanId()).isEqualTo(CLAN_ID);
        assertThat(response.items()).hasSize(1);
        LedgerHistoryItemResponse item = response.items().getFirst();
        assertThat(item.transactionId()).isEqualTo("LDG_DISB_01");
        assertThat(item.disbursementTitle()).isEqualTo("Tu sửa mái Từ đường");
        assertThat(item.disbursementCategory()).isEqualTo(CommonFundExpenseCategory.TempleMaintenance);
        assertThat(item.createdByName()).isEqualTo("Thủ Quỹ A");
        assertThat(item.approvedByName()).isEqualTo("Trưởng Tộc B");

        assertThat(response.summary().currentFundBalance()).isEqualByComparingTo("77000000.00");
        assertThat(response.summary().openingBalanceBeforePeriod()).isEqualByComparingTo("50000000.00");
        assertThat(response.summary().closingBalanceAtPeriodEnd()).isEqualByComparingTo("77000000.00");
        assertThat(response.summary().filteredTransactionCount()).isEqualTo(1L);
        assertThat(response.summary().filteredTotalInflow()).isEqualByComparingTo("0.00");
        assertThat(response.summary().filteredTotalOutflow()).isEqualByComparingTo("20000000.00");
        assertThat(response.summary().filteredNetAmount()).isEqualByComparingTo("-20000000.00");
    }

    @Test
    @DisplayName("Hỗ trợ định dạng thời gian ISO-8601 Instant và OffsetDateTime cho tham số from/to và mặc định sortDirection=DESC")
    void searchLedgerHistory_supportsIsoInstantAndOffsetDateTime() {
        when(immutableLedgerRepository.searchLedgerEntries(any(LedgerSearchCriteria.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(immutableLedgerRepository.aggregateFilteredFlow(any(LedgerSearchCriteria.class)))
                .thenReturn(new LedgerFilteredAggregates(0L, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        when(immutableLedgerRepository.findFirstByClanIdOrderByCreatedAtDescTransactionIdDesc(CLAN_ID))
                .thenReturn(Optional.empty());

        LedgerHistoryPageResponse res = service.searchLedgerHistory(
                "2026-11-01T00:00:00Z",
                "2026-11-15T12:30:00+07:00",
                null,
                null,
                null,
                null,
                null,
                0,
                20
        );

        ArgumentCaptor<LedgerSearchCriteria> criteriaCaptor = ArgumentCaptor.forClass(LedgerSearchCriteria.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(immutableLedgerRepository).searchLedgerEntries(criteriaCaptor.capture(), pageableCaptor.capture());

        assertThat(criteriaCaptor.getValue().fromInstant()).isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));
        assertThat(criteriaCaptor.getValue().toInstant()).isEqualTo(Instant.parse("2026-11-15T05:30:00Z"));
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(res.summary().currentFundBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Liên kết hai chiều: Giao dịch gốc hiển thị danh sách giao dịch điều chỉnh, netAdjustedAmount, pendingAdjustmentRequestCount; Giao dịch điều chỉnh hiển thị originalTransaction ngay cả khi gốc nằm ngoài bộ lọc")
    void bidirectionalLinking_originalToAdjustmentsAndAdjustmentToOriginal() {
        Instant t0 = Instant.parse("2026-11-10T01:00:00Z");
        Instant t1 = Instant.parse("2026-11-10T02:00:00Z");
        Instant t2 = Instant.parse("2026-11-10T03:00:00Z");

        ImmutableLedger originalTx = ImmutableLedger.builder()
                .transactionId("LDG_ORIG")
                .clanId(CLAN_ID)
                .eventId("EVT_A")
                .transactionType(LedgerTransactionType.SurplusTransfer)
                .amount(new BigDecimal("100000000.00"))
                .runningBalance(new BigDecimal("100000000.00"))
                .description("Kết dư sự kiện A")
                .createdByAccountId("ACC_TR")
                .approvedByAccountId("ACC_HEAD")
                .previousHash("GENESIS")
                .recordHash("a".repeat(64))
                .createdAt(t0)
                .build();

        ImmutableLedger adj1 = ImmutableLedger.builder()
                .transactionId("LDG_ADJ_1")
                .clanId(CLAN_ID)
                .eventId("EVT_A")
                .adjustmentRequestId("LAR_1")
                .originalTransactionId("LDG_ORIG")
                .transactionType(LedgerTransactionType.AdjustmentTransaction)
                .amount(new BigDecimal("-5000000.00"))
                .runningBalance(new BigDecimal("95000000.00"))
                .description("Điều chỉnh giảm 5tr")
                .createdByAccountId("ACC_TR")
                .approvedByAccountId("ACC_HEAD")
                .previousHash("a".repeat(64))
                .recordHash("b".repeat(64))
                .createdAt(t1)
                .build();

        ImmutableLedger adj2 = ImmutableLedger.builder()
                .transactionId("LDG_ADJ_2")
                .clanId(CLAN_ID)
                .eventId("EVT_A")
                .adjustmentRequestId("LAR_2")
                .originalTransactionId("LDG_ORIG")
                .transactionType(LedgerTransactionType.AdjustmentTransaction)
                .amount(new BigDecimal("2000000.00"))
                .runningBalance(new BigDecimal("97000000.00"))
                .description("Điều chỉnh tăng 2tr")
                .createdByAccountId("ACC_TR")
                .approvedByAccountId("ACC_HEAD")
                .previousHash("b".repeat(64))
                .recordHash("c".repeat(64))
                .createdAt(t2)
                .build();

        LedgerAdjustmentRequest req1 = LedgerAdjustmentRequest.builder()
                .requestId("LAR_1")
                .originalTransactionId("LDG_ORIG")
                .proposedAdjustmentAmount(new BigDecimal("-5000000.00"))
                .adjustmentReason("Thiếu hóa đơn điện")
                .evidenceAttachmentUrl("https://cdn.familyfund.vn/lar1.jpg")
                .proposedByAccountId("ACC_TR")
                .approvalStatus(LedgerAdjustmentStatus.Approved)
                .build();

        LedgerAdjustmentRequest req2 = LedgerAdjustmentRequest.builder()
                .requestId("LAR_2")
                .originalTransactionId("LDG_ORIG")
                .proposedAdjustmentAmount(new BigDecimal("2000000.00"))
                .adjustmentReason("Bổ sung tiền mừng")
                .evidenceAttachmentUrl("https://cdn.familyfund.vn/lar2.jpg")
                .proposedByAccountId("ACC_TR")
                .approvalStatus(LedgerAdjustmentStatus.Approved)
                .build();

        LedgerAdjustmentRequest pendingReq = LedgerAdjustmentRequest.builder()
                .requestId("LAR_PENDING")
                .originalTransactionId("LDG_ORIG")
                .proposedAdjustmentAmount(new BigDecimal("-1000000.00"))
                .adjustmentReason("Đang chờ duyệt")
                .proposedByAccountId("ACC_TR")
                .approvalStatus(LedgerAdjustmentStatus.Pending)
                .build();

        when(eventQueryService.findEventNamesByIds(anyCollection()))
                .thenReturn(Map.of("EVT_A", "Lễ Giỗ Tổ Họ Nguyễn"));
        when(identityRoleCommandService.findAccountDisplayNames(anyCollection()))
                .thenReturn(Map.of("ACC_TR", "Nguyễn Thị Thủ Quỹ", "ACC_HEAD", "Nguyễn Văn Trưởng Tộc"));
        when(ledgerAdjustmentRequestRepository.findAllById(any()))
                .thenReturn(List.of(req1, req2));

        // Chiều 2: Truy vấn chi tiết giao dịch gốc -> thấy đủ 2 giao dịch điều chỉnh + netAdjustedAmount = 97,000,000 + 1 pending request
        when(immutableLedgerRepository.findByTransactionIdAndClanId("LDG_ORIG", CLAN_ID))
                .thenReturn(Optional.of(originalTx));
        when(immutableLedgerRepository.findByClanIdAndOriginalTransactionIdInOrderByCreatedAtAscTransactionIdAsc(
                eq(CLAN_ID), anyCollection()))
                .thenReturn(List.of(adj1, adj2));
        when(ledgerAdjustmentRequestRepository.findByOriginalTransactionIdInAndApprovalStatus(
                anyCollection(), eq(LedgerAdjustmentStatus.Pending)))
                .thenReturn(List.of(pendingReq));

        LedgerHistoryItemResponse origDetail = service.getTransactionDetail("LDG_ORIG");
        assertThat(origDetail.transactionId()).isEqualTo("LDG_ORIG");
        assertThat(origDetail.eventName()).isEqualTo("Lễ Giỗ Tổ Họ Nguyễn");
        assertThat(origDetail.isAdjustment()).isFalse();
        assertThat(origDetail.hasAdjustments()).isTrue();
        assertThat(origDetail.adjustmentCount()).isEqualTo(2);
        assertThat(origDetail.netAdjustedAmount()).isEqualByComparingTo("97000000.00");
        assertThat(origDetail.pendingAdjustmentRequestCount()).isEqualTo(1);
        assertThat(origDetail.originalTransaction()).isNull();
        assertThat(origDetail.adjustmentTransactions()).hasSize(2);
        assertThat(origDetail.adjustmentTransactions().get(0).transactionId()).isEqualTo("LDG_ADJ_1");
        assertThat(origDetail.adjustmentTransactions().get(0).adjustmentReason()).isEqualTo("Thiếu hóa đơn điện");
        assertThat(origDetail.adjustmentTransactions().get(0).createdByName()).isEqualTo("Nguyễn Thị Thủ Quỹ");
        assertThat(origDetail.adjustmentTransactions().get(1).transactionId()).isEqualTo("LDG_ADJ_2");
        assertThat(origDetail.adjustmentTransactions().get(1).amount()).isEqualByComparingTo("2000000.00");

        // Chiều 1: Khi lọc chỉ lấy AdjustmentTransaction (giao dịch gốc không nằm trong trang kết quả), vẫn nạp đầy đủ originalTransaction
        when(immutableLedgerRepository.searchLedgerEntries(any(LedgerSearchCriteria.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(adj1)));
        when(immutableLedgerRepository.aggregateFilteredFlow(any(LedgerSearchCriteria.class)))
                .thenReturn(new LedgerFilteredAggregates(1L, BigDecimal.ZERO, new BigDecimal("5000000.00"), new BigDecimal("-5000000.00")));
        when(immutableLedgerRepository.findFirstByClanIdOrderByCreatedAtDescTransactionIdDesc(CLAN_ID))
                .thenReturn(Optional.of(adj2));
        when(immutableLedgerRepository.findByClanIdAndOriginalTransactionIdInOrderByCreatedAtAscTransactionIdAsc(
                eq(CLAN_ID), anyCollection()))
                .thenReturn(List.of());
        when(immutableLedgerRepository.findByClanIdAndTransactionIdIn(eq(CLAN_ID), anyCollection()))
                .thenReturn(List.of(originalTx));

        LedgerHistoryPageResponse adjFilteredPage = service.searchLedgerHistory(
                null, null, List.of(LedgerTransactionType.AdjustmentTransaction),
                null, null, null, "DESC", 0, 20
        );
        assertThat(adjFilteredPage.items()).hasSize(1);
        LedgerHistoryItemResponse adjItem = adjFilteredPage.items().getFirst();
        assertThat(adjItem.transactionId()).isEqualTo("LDG_ADJ_1");
        assertThat(adjItem.isAdjustment()).isTrue();
        assertThat(adjItem.originalTransactionId()).isEqualTo("LDG_ORIG");
        assertThat(adjItem.adjustmentReason()).isEqualTo("Thiếu hóa đơn điện");
        assertThat(adjItem.evidenceAttachmentUrl()).isEqualTo("https://cdn.familyfund.vn/lar1.jpg");
        assertThat(adjItem.originalTransaction()).isNotNull();
        assertThat(adjItem.originalTransaction().transactionId()).isEqualTo("LDG_ORIG");
        assertThat(adjItem.originalTransaction().amount()).isEqualByComparingTo("100000000.00");
        assertThat(adjItem.originalTransaction().runningBalance()).isEqualByComparingTo("100000000.00");
        assertThat(adjItem.originalTransaction().description()).isEqualTo("Kết dư sự kiện A");
        assertThat(adjItem.originalTransaction().createdByName()).isEqualTo("Nguyễn Thị Thủ Quỹ");
        assertThat(adjItem.originalTransaction().approvedByName()).isEqualTo("Nguyễn Văn Trưởng Tộc");
    }

    @Test
    @DisplayName("Validation các trường hợp biên: from > to, sai định dạng ngày, page < 0, size ngoài [1..100], sortDirection không hợp lệ, thiếu clanId và transactionId không tồn tại")
    void validationEdgeCases_throwExpectedExceptions() {
        // 1. from > to
        assertThatThrownBy(() -> service.searchLedgerHistory(
                "2026-11-30", "2026-11-01", null, null, null, null, "DESC", 0, 20))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("from");

        // 2. Malformed from / to
        assertThatThrownBy(() -> service.searchLedgerHistory(
                "not-a-date", null, null, null, null, null, "DESC", 0, 20))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("from");

        assertThatThrownBy(() -> service.searchLedgerHistory(
                null, "31/11/2026", null, null, null, null, "DESC", 0, 20))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("to");

        // 3. page < 0, size < 1, size > 100
        assertThatThrownBy(() -> service.searchLedgerHistory(
                null, null, null, null, null, null, "DESC", -1, 20))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("page");

        assertThatThrownBy(() -> service.searchLedgerHistory(
                null, null, null, null, null, null, "DESC", 0, 0))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("size");

        assertThatThrownBy(() -> service.searchLedgerHistory(
                null, null, null, null, null, null, "DESC", 0, 101))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("size");

        // 4. Invalid sortDirection
        assertThatThrownBy(() -> service.searchLedgerHistory(
                null, null, null, null, null, null, "RANDOM", 0, 20))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("sortDirection");

        // 5. Non-existent or blank transactionId on getTransactionDetail -> ResourceNotFoundException
        when(immutableLedgerRepository.findByTransactionIdAndClanId("LDG_NOT_FOUND", CLAN_ID))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getTransactionDetail("LDG_NOT_FOUND"))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.getTransactionDetail("   "))
                .isInstanceOf(ResourceNotFoundException.class);

        // 6. Missing clanId -> BusinessRuleException
        try (var mockedTenant = org.mockito.Mockito.mockStatic(TenantContext.class)) {
            mockedTenant.when(TenantContext::getClanId).thenReturn("  ");
            assertThatThrownBy(() -> service.searchLedgerHistory(
                    null, null, null, null, null, null, "DESC", 0, 20))
                    .isInstanceOf(BusinessRuleException.class);
            assertThatThrownBy(() -> service.getTransactionDetail("LDG_ORIG"))
                    .isInstanceOf(BusinessRuleException.class);
        }
    }

    @Test
    @DisplayName("Fallback netAmount khi aggregateFilteredFlow trả netAmount == null phải tính totalInflow - totalOutflow")
    void searchLedgerHistory_nullAggregateNetAmount_subtractsOutflowFromInflow() {
        when(immutableLedgerRepository.searchLedgerEntries(any(LedgerSearchCriteria.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(immutableLedgerRepository.aggregateFilteredFlow(any(LedgerSearchCriteria.class)))
                .thenReturn(new LedgerFilteredAggregates(
                        2L,
                        new BigDecimal("100000000.00"),
                        new BigDecimal("25000000.00"),
                        null
                ));
        when(immutableLedgerRepository.findFirstByClanIdOrderByCreatedAtDescTransactionIdDesc(CLAN_ID))
                .thenReturn(Optional.empty());

        LedgerHistoryPageResponse res = service.searchLedgerHistory(
                null, null, null, null, null, null, "DESC", 0, 20
        );

        assertThat(res.summary().filteredTotalInflow()).isEqualByComparingTo("100000000.00");
        assertThat(res.summary().filteredTotalOutflow()).isEqualByComparingTo("25000000.00");
        assertThat(res.summary().filteredNetAmount()).isEqualByComparingTo("75000000.00");
    }
}
