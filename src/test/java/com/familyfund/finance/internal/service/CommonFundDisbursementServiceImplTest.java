package com.familyfund.finance.internal.service;

import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.core.security.SystemRoles;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.event.api.EventQueryService;
import com.familyfund.event.api.dto.EventRole;
import com.familyfund.finance.api.dto.CommonFundDisbursementStatus;
import com.familyfund.finance.api.dto.CommonFundExpenseCategory;
import com.familyfund.finance.api.dto.LedgerTransactionType;
import com.familyfund.finance.internal.dto.request.ProposeCommonFundDisbursementRequest;
import com.familyfund.finance.internal.dto.request.ReviewCommonFundDisbursementRequest;
import com.familyfund.finance.internal.dto.response.CommonFundBalanceResponse;
import com.familyfund.finance.internal.dto.response.CommonFundDisbursementResponse;
import com.familyfund.finance.internal.dto.response.ReviewCommonFundDisbursementResponse;
import com.familyfund.finance.internal.entity.CommonFundDisbursementRequest;
import com.familyfund.finance.internal.entity.ImmutableLedger;
import com.familyfund.finance.internal.repository.CommonFundDisbursementRequestRepository;
import com.familyfund.finance.internal.repository.ImmutableLedgerRepository;
import com.familyfund.finance.internal.util.CommonFundDisbursementIdGenerator;
import com.familyfund.finance.internal.util.ImmutableLedgerHashCalculator;
import com.familyfund.finance.internal.util.ImmutableLedgerIdGenerator;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.genealogy.api.dto.MemberSummaryDto;
import com.familyfund.identity.api.AccountPasswordVerificationService;
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
class CommonFundDisbursementServiceImplTest {

    private static final String CLAN_ID = "CLN_TEST";
    private static final String OTHER_CLAN_ID = "CLN_OTHER";
    private static final String ACC_HEAD = "ACC_CLAN_HEAD";
    private static final String ACC_PROPOSER = "ACC_TREASURER";
    private static final String MEM_TREASURER = "MEM_TREASURER";
    private static final String MEM_BENEFICIARY = "MEM_SCHOLAR_01";

    @Mock
    private CommonFundDisbursementRequestRepository disbursementRepository;

    @Mock
    private ImmutableLedgerRepository immutableLedgerRepository;

    @Mock
    private CommonFundDisbursementIdGenerator disbursementIdGenerator;

    @Mock
    private ImmutableLedgerIdGenerator ledgerIdGenerator;

    @Spy
    private ImmutableLedgerHashCalculator ledgerHashCalculator = new ImmutableLedgerHashCalculator();

    @Mock
    private EventQueryService eventQueryService;

    @Mock
    private GenealogyQueryService genealogyQueryService;

    @Mock
    private IdentityRoleCommandService identityRoleCommandService;

    @Mock
    private AccountPasswordVerificationService accountPasswordVerificationService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private CommonFundDisbursementServiceImpl service;

    private ImmutableLedger latestLedger;

    @BeforeEach
    void setUp() {
        TenantContext.setClanId(CLAN_ID);
        latestLedger = ImmutableLedger.builder()
                .transactionId("LDG_LATEST_01")
                .clanId(CLAN_ID)
                .transactionType(LedgerTransactionType.SurplusTransfer)
                .amount(new BigDecimal("100000000.00"))
                .runningBalance(new BigDecimal("100000000.00"))
                .description("Kết dư đại lễ")
                .createdByAccountId(ACC_PROPOSER)
                .approvedByAccountId(ACC_HEAD)
                .recordHash("a".repeat(64))
                .createdAt(Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS))
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("getFundBalance: Trả về số dư khả dụng và tổng tiền phiếu chi Pending, hoặc 0 khi sổ cái trống")
    void getFundBalance_returnsSnapshotOrZeroWhenEmpty() {
        when(immutableLedgerRepository.findFirstByClanIdOrderByCreatedAtDescTransactionIdDesc(CLAN_ID))
                .thenReturn(Optional.of(latestLedger));
        when(disbursementRepository.sumPendingAmountByClanId(CLAN_ID))
                .thenReturn(new BigDecimal("25000000.00"));

        CommonFundBalanceResponse balance = service.getFundBalance();
        assertThat(balance.clanId()).isEqualTo(CLAN_ID);
        assertThat(balance.availableBalance()).isEqualByComparingTo("100000000.00");
        assertThat(balance.pendingDisbursementAmount()).isEqualByComparingTo("25000000.00");
        assertThat(balance.latestTransactionId()).isEqualTo("LDG_LATEST_01");

        when(immutableLedgerRepository.findFirstByClanIdOrderByCreatedAtDescTransactionIdDesc(CLAN_ID))
                .thenReturn(Optional.empty());
        when(disbursementRepository.sumPendingAmountByClanId(CLAN_ID)).thenReturn(null);

        CommonFundBalanceResponse emptyBalance = service.getFundBalance();
        assertThat(emptyBalance.availableBalance()).isEqualByComparingTo("0.00");
        assertThat(emptyBalance.pendingDisbursementAmount()).isEqualByComparingTo("0.00");
        assertThat(emptyBalance.latestTransactionId()).isNull();
    }

    @Test
    @DisplayName("proposeDisbursement: EventTreasurer đề xuất chi khuyến học kèm beneficiaryMemberId tự động điền tên thành công")
    void proposeDisbursement_byEventTreasurerWithBeneficiaryMember_success() {
        ProposeCommonFundDisbursementRequest request = new ProposeCommonFundDisbursementRequest(
                "Trao học bổng sinh viên xuất sắc 2026",
                CommonFundExpenseCategory.Scholarship,
                new BigDecimal("15000000.00"),
                null,
                MEM_BENEFICIARY,
                "Thưởng cháu đỗ thủ khoa đại học",
                "https://storage.familyfund.vn/disbursements/hocbong.pdf"
        );

        when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
        when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(false);
        when(currentUserProvider.hasAuthority(SystemRoles.BRANCH_HEAD)).thenReturn(false);
        when(currentUserProvider.currentMemberId()).thenReturn(Optional.of(MEM_TREASURER));
        when(eventQueryService.hasEventRoleInClan(CLAN_ID, MEM_TREASURER, EventRole.EventTreasurer))
                .thenReturn(true);
        when(genealogyQueryService.findMemberSummary(MEM_BENEFICIARY))
                .thenReturn(Optional.of(new MemberSummaryDto(MEM_BENEFICIARY, "Nguyễn Minh Khôi", CLAN_ID, "BR_01")));
        when(immutableLedgerRepository.findFirstByClanIdOrderByCreatedAtDescTransactionIdDesc(CLAN_ID))
                .thenReturn(Optional.of(latestLedger));
        when(currentUserProvider.currentAccountId()).thenReturn(Optional.of(ACC_PROPOSER));
        when(disbursementIdGenerator.generate()).thenReturn("CFD0001TEST01");
        when(disbursementRepository.existsById("CFD0001TEST01")).thenReturn(false);
        when(disbursementRepository.save(any(CommonFundDisbursementRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(identityRoleCommandService.findActiveAccountsByClan(CLAN_ID)).thenReturn(List.of(
                new AccountSummaryDto(ACC_HEAD, "MEM_HEAD", "0900000001", null, "Active", Set.of(SystemRoles.CLAN_HEAD)),
                new AccountSummaryDto(ACC_PROPOSER, MEM_TREASURER, "0900000002", null, "Active", Set.of(SystemRoles.MEMBER))
        ));

        CommonFundDisbursementResponse response = service.proposeDisbursement(request);

        assertThat(response.requestId()).isEqualTo("CFD0001TEST01");
        assertThat(response.category()).isEqualTo(CommonFundExpenseCategory.Scholarship);
        assertThat(response.amount()).isEqualByComparingTo("15000000.00");
        assertThat(response.beneficiaryName()).isEqualTo("Nguyễn Minh Khôi");
        assertThat(response.approvalStatus()).isEqualTo(CommonFundDisbursementStatus.Pending);

        ArgumentCaptor<NotificationCommand> notifCaptor = ArgumentCaptor.forClass(NotificationCommand.class);
        verify(notificationService).notify(notifCaptor.capture());
        assertThat(notifCaptor.getValue().recipientAccountId()).isEqualTo(ACC_HEAD);
        assertThat(notifCaptor.getValue().category()).isEqualTo(NotificationCategory.Finance);
        assertThat(notifCaptor.getValue().referenceType()).isEqualTo("COMMON_FUND_DISBURSEMENT");
    }

    @Test
    @DisplayName("proposeDisbursement: Trưởng chi (ROLE_BRANCH_HEAD) bị chặn 400 nếu vượt quá running_balance khả dụng")
    void proposeDisbursement_insufficientBalance_throwsBusinessRule() {
        ProposeCommonFundDisbursementRequest request = new ProposeCommonFundDisbursementRequest(
                "Tu sửa toàn bộ cổng tam quan",
                CommonFundExpenseCategory.TempleMaintenance,
                new BigDecimal("150000000.00"),
                "Đội thợ nề Đồng Kỵ",
                null,
                "Thi công vượt ngân sách hiện có",
                null
        );

        when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
        when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(false);
        when(currentUserProvider.hasAuthority(SystemRoles.BRANCH_HEAD)).thenReturn(true);
        when(immutableLedgerRepository.findFirstByClanIdOrderByCreatedAtDescTransactionIdDesc(CLAN_ID))
                .thenReturn(Optional.of(latestLedger));

        assertThatThrownBy(() -> service.proposeDisbursement(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("không đủ");

        verify(disbursementRepository, never()).save(any());
    }

    @Test
    @DisplayName("proposeDisbursement: Thành viên thường không giữ vai trò EventTreasurer bị chặn AccessDeniedException")
    void proposeDisbursement_unauthorizedMember_throwsAccessDenied() {
        ProposeCommonFundDisbursementRequest request = new ProposeCommonFundDisbursementRequest(
                "Đề xuất không đủ quyền",
                CommonFundExpenseCategory.Other,
                new BigDecimal("1000000.00"),
                null,
                null,
                "Thử vượt quyền",
                null
        );

        when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
        when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(false);
        when(currentUserProvider.hasAuthority(SystemRoles.BRANCH_HEAD)).thenReturn(false);
        when(currentUserProvider.currentMemberId()).thenReturn(Optional.of("MEM_ORDINARY"));
        when(eventQueryService.hasEventRoleInClan(CLAN_ID, "MEM_ORDINARY", EventRole.EventTreasurer))
                .thenReturn(false);

        assertThatThrownBy(() -> service.proposeDisbursement(request))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("proposeDisbursement: Thành viên thụ hưởng không tồn tại hoặc thuộc dòng họ khác -> ném ResourceNotFoundException")
    void proposeDisbursement_crossClanBeneficiary_throwsNotFound() {
        ProposeCommonFundDisbursementRequest request = new ProposeCommonFundDisbursementRequest(
                "Hỗ trợ con cháu",
                CommonFundExpenseCategory.ReliefSupport,
                new BigDecimal("5000000.00"),
                null,
                "MEM_FOREIGN",
                "Kiểm tra cô lập dòng họ",
                null
        );

        when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(true);
        when(genealogyQueryService.findMemberSummary("MEM_FOREIGN"))
                .thenReturn(Optional.of(new MemberSummaryDto("MEM_FOREIGN", "Người họ khác", OTHER_CLAN_ID, "BR_X")));

        assertThatThrownBy(() -> service.proposeDisbursement(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("reviewDisbursement: Trưởng tộc duyệt kèm mật khẩu đúng -> ghi -amount loại CommonFundDisbursement, giảm running_balance và nối SHA-256")
    void reviewDisbursement_approveWithValidPassword_appendsNegativeLedgerAndReducesBalance() {
        CommonFundDisbursementRequest pending = buildPendingRequest("CFD_REV_01", new BigDecimal("45000000.00"));
        ReviewCommonFundDisbursementRequest review = new ReviewCommonFundDisbursementRequest(
                "CFD_REV_01", true, "ClanHead@123", null, "Giải ngân tu sửa mái ngói Từ đường"
        );

        when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
        when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(true);
        when(currentUserProvider.currentAccountId()).thenReturn(Optional.of(ACC_HEAD));
        when(accountPasswordVerificationService.verifyActiveAccountPassword(ACC_HEAD, "ClanHead@123"))
                .thenReturn(true);
        when(disbursementRepository.findByIdForUpdate("CFD_REV_01")).thenReturn(Optional.of(pending));
        when(immutableLedgerRepository.acquireClanLedgerLock(CLAN_ID)).thenReturn(1);
        when(immutableLedgerRepository.findFirstByClanIdOrderByCreatedAtDescTransactionIdDesc(CLAN_ID))
                .thenReturn(Optional.of(latestLedger));
        when(ledgerIdGenerator.generate()).thenReturn("LDG0001CFD0001");
        when(immutableLedgerRepository.existsById("LDG0001CFD0001")).thenReturn(false);
        when(immutableLedgerRepository.saveAndFlush(any(ImmutableLedger.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(disbursementRepository.save(any(CommonFundDisbursementRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ReviewCommonFundDisbursementResponse response = service.reviewDisbursement("CFD_REV_01", review);

        verify(immutableLedgerRepository).acquireClanLedgerLock(CLAN_ID);
        assertThat(response.disbursementRequest().approvalStatus()).isEqualTo(CommonFundDisbursementStatus.Approved);
        assertThat(response.disbursementRequest().reviewedByAccountId()).isEqualTo(ACC_HEAD);
        assertThat(response.disbursementRequest().ledgerTransactionId()).isEqualTo("LDG0001CFD0001");

        assertThat(response.ledgerEntry()).isNotNull();
        assertThat(response.ledgerEntry().transactionType()).isEqualTo(LedgerTransactionType.CommonFundDisbursement);
        assertThat(response.ledgerEntry().disbursementRequestId()).isEqualTo("CFD_REV_01");
        assertThat(response.ledgerEntry().amount()).isEqualByComparingTo("-45000000.00");
        assertThat(response.ledgerEntry().runningBalance()).isEqualByComparingTo("55000000.00");
        assertThat(response.ledgerEntry().previousHash()).isEqualTo(latestLedger.getRecordHash());
        assertThat(response.ledgerEntry().recordHash()).matches("^[0-9a-f]{64}$");

        ArgumentCaptor<ImmutableLedger> ledgerCaptor = ArgumentCaptor.forClass(ImmutableLedger.class);
        verify(immutableLedgerRepository).saveAndFlush(ledgerCaptor.capture());
        assertThat(ledgerHashCalculator.verifyRecordHash(ledgerCaptor.getValue(), latestLedger.getRecordHash())).isTrue();

        ArgumentCaptor<NotificationCommand> notifCaptor = ArgumentCaptor.forClass(NotificationCommand.class);
        verify(notificationService).notify(notifCaptor.capture());
        assertThat(notifCaptor.getValue().recipientAccountId()).isEqualTo(ACC_PROPOSER);
        assertThat(notifCaptor.getValue().referenceType()).isEqualTo("COMMON_FUND_DISBURSEMENT");
    }

    @Test
    @DisplayName("reviewDisbursement: Thiếu hoặc sai mật khẩu xác nhận khi duyệt -> ném BusinessRuleException và không chạm DB lock")
    void reviewDisbursement_missingOrInvalidPassword_throwsBusinessRule() {
        when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
        when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(true);
        when(currentUserProvider.currentAccountId()).thenReturn(Optional.of(ACC_HEAD));

        ReviewCommonFundDisbursementRequest missingPass = new ReviewCommonFundDisbursementRequest(
                "CFD_01", true, "   ", null, null
        );
        assertThatThrownBy(() -> service.reviewDisbursement("CFD_01", missingPass))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Mật khẩu xác nhận");

        when(accountPasswordVerificationService.verifyActiveAccountPassword(ACC_HEAD, "WrongPass"))
                .thenReturn(false);
        ReviewCommonFundDisbursementRequest wrongPass = new ReviewCommonFundDisbursementRequest(
                "CFD_01", true, "WrongPass", null, null
        );
        assertThatThrownBy(() -> service.reviewDisbursement("CFD_01", wrongPass))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("không chính xác");

        verify(immutableLedgerRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("reviewDisbursement: Chặn phê duyệt (400) nếu số dư thực tế tại thời điểm duyệt không đủ")
    void reviewDisbursement_insufficientBalanceAtApproval_throwsBusinessRuleWithoutWrite() {
        CommonFundDisbursementRequest largePending = buildPendingRequest("CFD_BIG_01", new BigDecimal("120000000.00"));
        ReviewCommonFundDisbursementRequest review = new ReviewCommonFundDisbursementRequest(
                "CFD_BIG_01", true, "ClanHead@123", null, null
        );

        when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
        when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(true);
        when(currentUserProvider.currentAccountId()).thenReturn(Optional.of(ACC_HEAD));
        when(accountPasswordVerificationService.verifyActiveAccountPassword(ACC_HEAD, "ClanHead@123"))
                .thenReturn(true);
        when(disbursementRepository.findByIdForUpdate("CFD_BIG_01")).thenReturn(Optional.of(largePending));
        when(immutableLedgerRepository.acquireClanLedgerLock(CLAN_ID)).thenReturn(1);
        when(immutableLedgerRepository.findFirstByClanIdOrderByCreatedAtDescTransactionIdDesc(CLAN_ID))
                .thenReturn(Optional.of(latestLedger));

        assertThatThrownBy(() -> service.reviewDisbursement(null, review))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("không đủ");

        verify(immutableLedgerRepository, never()).saveAndFlush(any());
        verify(disbursementRepository, never()).save(any());
    }

    @Test
    @DisplayName("reviewDisbursement: Từ chối không cần mật khẩu, bắt buộc lý do, lưu Rejected và không ghi Sổ cái")
    void reviewDisbursement_rejectFlow_andValidation() {
        when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
        when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(true);
        when(currentUserProvider.currentAccountId()).thenReturn(Optional.of(ACC_HEAD));

        ReviewCommonFundDisbursementRequest missingReason = new ReviewCommonFundDisbursementRequest(
                "CFD_REJ_01", false, null, "  ", null
        );
        assertThatThrownBy(() -> service.reviewDisbursement("CFD_REJ_01", missingReason))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Lý do từ chối");

        CommonFundDisbursementRequest pending = buildPendingRequest("CFD_REJ_01", new BigDecimal("20000000.00"));
        when(disbursementRepository.findByIdForUpdate("CFD_REJ_01")).thenReturn(Optional.of(pending));
        when(disbursementRepository.save(any(CommonFundDisbursementRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ReviewCommonFundDisbursementRequest validReject = new ReviewCommonFundDisbursementRequest(
                null, false, null, "Cần bổ sung 2 báo giá nhà thầu", null
        );
        ReviewCommonFundDisbursementResponse response = service.reviewDisbursement("CFD_REJ_01", validReject);

        assertThat(response.ledgerEntry()).isNull();
        assertThat(response.disbursementRequest().approvalStatus()).isEqualTo(CommonFundDisbursementStatus.Rejected);
        assertThat(response.disbursementRequest().rejectionReason()).isEqualTo("Cần bổ sung 2 báo giá nhà thầu");

        verify(accountPasswordVerificationService, never()).verifyActiveAccountPassword(any(), any());
        verify(immutableLedgerRepository, never()).acquireClanLedgerLock(any());
        verify(immutableLedgerRepository, never()).saveAndFlush(any());
        verify(notificationService).notify(any(NotificationCommand.class));
    }

    @Test
    @DisplayName("reviewDisbursement: Sai quyền (403), đã duyệt trước đó (400), khác dòng họ (404) và lỗi thông báo không rollback")
    void reviewDisbursement_edgeCasesAndResilience() {
        when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
        when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(false);
        assertThatThrownBy(() -> service.reviewDisbursement("CFD_01",
                new ReviewCommonFundDisbursementRequest("CFD_01", false, null, "Lý do", null)))
                .isInstanceOf(AccessDeniedException.class);

        when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(true);
        when(currentUserProvider.currentAccountId()).thenReturn(Optional.of("ACC_ADMIN"));

        CommonFundDisbursementRequest otherClanReq = CommonFundDisbursementRequest.builder()
                .requestId("CFD_OTHER").clanId(OTHER_CLAN_ID).title("Other")
                .category(CommonFundExpenseCategory.Other).amount(new BigDecimal("1000000.00"))
                .purpose("Test").proposedByAccountId("ACC_X")
                .approvalStatus(CommonFundDisbursementStatus.Pending).createdAt(Instant.now()).build();
        when(disbursementRepository.findByIdForUpdate("CFD_OTHER")).thenReturn(Optional.of(otherClanReq));
        assertThatThrownBy(() -> service.reviewDisbursement("CFD_OTHER",
                new ReviewCommonFundDisbursementRequest(null, false, null, "Từ chối", null)))
                .isInstanceOf(ResourceNotFoundException.class);

        CommonFundDisbursementRequest alreadyApproved = buildPendingRequest("CFD_DONE", new BigDecimal("5000000.00"));
        alreadyApproved.setApprovalStatus(CommonFundDisbursementStatus.Approved);
        when(disbursementRepository.findByIdForUpdate("CFD_DONE")).thenReturn(Optional.of(alreadyApproved));
        assertThatThrownBy(() -> service.reviewDisbursement("CFD_DONE",
                new ReviewCommonFundDisbursementRequest(null, false, null, "Từ chối muộn", null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Approved");

        CommonFundDisbursementRequest notifFailReq = buildPendingRequest("CFD_NOTIF", new BigDecimal("5000000.00"));
        when(accountPasswordVerificationService.verifyActiveAccountPassword("ACC_ADMIN", "Admin@123"))
                .thenReturn(true);
        when(disbursementRepository.findByIdForUpdate("CFD_NOTIF")).thenReturn(Optional.of(notifFailReq));
        when(immutableLedgerRepository.acquireClanLedgerLock(CLAN_ID)).thenReturn(1);
        when(immutableLedgerRepository.findFirstByClanIdOrderByCreatedAtDescTransactionIdDesc(CLAN_ID))
                .thenReturn(Optional.of(latestLedger));
        when(ledgerIdGenerator.generate()).thenReturn("LDG0002NOTIF");
        when(immutableLedgerRepository.existsById("LDG0002NOTIF")).thenReturn(false);
        when(immutableLedgerRepository.saveAndFlush(any(ImmutableLedger.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(disbursementRepository.save(any(CommonFundDisbursementRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("Inbox error"))
                .when(notificationService).notify(any(NotificationCommand.class));

        ReviewCommonFundDisbursementResponse ok = service.reviewDisbursement("CFD_NOTIF",
                new ReviewCommonFundDisbursementRequest(null, true, "Admin@123", null, null));
        assertThat(ok.disbursementRequest().approvalStatus()).isEqualTo(CommonFundDisbursementStatus.Approved);
        assertThat(ok.ledgerEntry().transactionId()).isEqualTo("LDG0002NOTIF");
    }

    private static CommonFundDisbursementRequest buildPendingRequest(String requestId, BigDecimal amount) {
        return CommonFundDisbursementRequest.builder()
                .requestId(requestId)
                .clanId(CLAN_ID)
                .title("Tu sửa mái ngói Từ đường")
                .category(CommonFundExpenseCategory.TempleMaintenance)
                .amount(amount)
                .beneficiaryName("Đội thợ nề Đồng Kỵ")
                .purpose("Chống dột hậu cung trước mùa mưa")
                .proposedByAccountId(ACC_PROPOSER)
                .approvalStatus(CommonFundDisbursementStatus.Pending)
                .createdAt(Instant.now().minus(10, ChronoUnit.MINUTES))
                .build();
    }
}
