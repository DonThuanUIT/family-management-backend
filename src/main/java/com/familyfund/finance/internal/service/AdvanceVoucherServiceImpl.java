package com.familyfund.finance.internal.service;

import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.core.security.SystemRoles;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.event.api.EventQueryService;
import com.familyfund.event.api.dto.EventRole;
import com.familyfund.event.api.dto.EventSummaryDto;
import com.familyfund.event.api.dto.EventTaskSummaryDto;
import com.familyfund.finance.api.dto.AdvanceVoucherStatus;
import com.familyfund.finance.internal.dto.request.ConfirmDisbursementRequest;
import com.familyfund.finance.internal.dto.request.CreateAdvanceVoucherRequest;
import com.familyfund.finance.internal.dto.response.AdvanceVoucherResponse;
import com.familyfund.finance.internal.entity.AdvanceVoucher;
import com.familyfund.finance.internal.repository.AdvanceVoucherRepository;
import com.familyfund.finance.internal.util.AdvanceVoucherIdGenerator;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.genealogy.api.dto.MemberSummaryDto;
import com.familyfund.identity.api.IdentityRoleCommandService;
import com.familyfund.identity.api.dto.AccountSummaryDto;
import com.familyfund.notification.api.NotificationCategory;
import com.familyfund.notification.api.NotificationCommand;
import com.familyfund.notification.api.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Service implementation for managing event advance vouchers (US-46).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdvanceVoucherServiceImpl implements AdvanceVoucherService {

    private final AdvanceVoucherRepository advanceVoucherRepository;
    private final AdvanceVoucherIdGenerator advanceVoucherIdGenerator;
    private final EventQueryService eventQueryService;
    private final GenealogyQueryService genealogyQueryService;
    private final IdentityRoleCommandService identityRoleCommandService;
    private final NotificationService notificationService;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdvanceVoucherResponse createAdvanceVoucher(CreateAdvanceVoucherRequest request) {
        String clanId = TenantContext.getClanId();

        // 1. Kiểm tra sự tồn tại của hạng mục công việc (task)
        EventTaskSummaryDto task = eventQueryService.findTaskSummary(request.taskId())
                .orElseThrow(() -> new ResourceNotFoundException("Hạng mục công việc", "taskId", request.taskId()));

        // Kiểm tra đa dòng họ (multi-tenancy)
        if (clanId != null && !clanId.isBlank() && task.clanId() != null && !clanId.equals(task.clanId())) {
            throw new ResourceNotFoundException("Hạng mục công việc", "taskId", request.taskId());
        }
        String effectiveClanId = task.clanId() != null ? task.clanId() : clanId;

        // 2. Kiểm tra trạng thái sự kiện: không cho phép tạo phiếu khi sự kiện đã đóng
        if (eventQueryService.isEventClosed(task.eventId())) {
            throw new BusinessRuleException("Không thể lập phiếu tạm ứng cho sự kiện đã đóng (Closed)");
        }

        // 3. Phân quyền theo ngữ cảnh BTC: CLAN_HEAD / ADMIN hoặc nhân sự BTC sự kiện (HeadOfOrganizer, OrganizerMember, EventTreasurer)
        validateBtcAuthorization(task.eventId(), effectiveClanId);

        // 4. Kiểm tra thành viên nhận tiền (recipientMemberId) tồn tại trong cùng dòng họ
        MemberSummaryDto recipientMember = genealogyQueryService.findMemberSummary(request.recipientMemberId())
                .orElseThrow(() -> new ResourceNotFoundException("Thành viên nhận tiền tạm ứng", "recipientMemberId", request.recipientMemberId()));
        if (effectiveClanId != null && !effectiveClanId.equals(recipientMember.clanId())) {
            throw new ResourceNotFoundException("Thành viên nhận tiền tạm ứng", "recipientMemberId", request.recipientMemberId());
        }

        // 5. Kiểm tra tính hợp lệ của mốc thời gian: hạn nộp chứng từ phải sau ngày nhận tiền
        if (request.settlementDeadline().isBefore(request.receivedDate())) {
            throw new BusinessRuleException("Hạn nộp chứng từ quyết toán phải sau ngày nhận tiền tạm ứng");
        }

        // 6. Phát sinh hoặc xác thực mã phiếu tạm ứng (ADV...)
        String voucherId;
        if (request.advanceVoucherId() != null && !request.advanceVoucherId().isBlank()) {
            voucherId = request.advanceVoucherId().trim();
            if (advanceVoucherRepository.existsById(voucherId)) {
                throw new BusinessRuleException("Mã phiếu tạm ứng đã tồn tại: " + voucherId);
            }
        } else {
            voucherId = generateUniqueAdvanceVoucherId();
        }

        String creatorAccountId = currentUserProvider.currentAccountId().orElse("SYSTEM");

        AdvanceVoucher voucher = AdvanceVoucher.builder()
                .advanceVoucherId(voucherId)
                .taskId(task.taskId())
                .clanId(effectiveClanId)
                .recipientMemberId(recipientMember.memberId())
                .advanceAmount(request.advanceAmount())
                .receivedDate(request.receivedDate())
                .settlementDeadline(request.settlementDeadline())
                .commitmentNotes(request.commitmentNotes() != null ? request.commitmentNotes().trim() : null)
                .createdByAccountId(creatorAccountId)
                .status(AdvanceVoucherStatus.ChoGiaoTien)
                .build();

        AdvanceVoucher saved = advanceVoucherRepository.save(voucher);
        log.info("US-46: Lập phiếu tạm ứng {} (số tiền: {}, người nhận: {}) cho task {}",
                saved.getAdvanceVoucherId(), saved.getAdvanceAmount(), saved.getRecipientMemberId(), saved.getTaskId());

        // Thông báo cho người nhận (Category Finance)
        notifyRecipientOnCreated(saved, task.taskName());

        return toResponse(saved, task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdvanceVoucherResponse confirmDisbursement(String advanceVoucherId, ConfirmDisbursementRequest request) {
        String clanId = TenantContext.getClanId();

        AdvanceVoucher voucher = advanceVoucherRepository.findById(advanceVoucherId)
                .orElseThrow(() -> new ResourceNotFoundException("Phiếu tạm ứng", "advanceVoucherId", advanceVoucherId));

        if (clanId != null && !clanId.isBlank() && voucher.getClanId() != null && !clanId.equals(voucher.getClanId())) {
            throw new ResourceNotFoundException("Phiếu tạm ứng", "advanceVoucherId", advanceVoucherId);
        }

        EventTaskSummaryDto task = eventQueryService.findTaskSummary(voucher.getTaskId())
                .orElseThrow(() -> new ResourceNotFoundException("Hạng mục công việc", "taskId", voucher.getTaskId()));

        // Kiểm tra sự kiện đã đóng
        if (eventQueryService.isEventClosed(task.eventId())) {
            throw new BusinessRuleException("Không thể xác nhận giao tiền cho sự kiện đã đóng (Closed)");
        }

        // Phân quyền Thủ quỹ: CLAN_HEAD, ADMIN hoặc vai trò EventTreasurer trong sự kiện
        validateTreasurerAuthorization(task.eventId(), voucher.getClanId());

        // Kiểm tra trạng thái: chỉ phiếu đang ở ChoGiaoTien mới được xác nhận giao tiền
        if (voucher.getStatus() == AdvanceVoucherStatus.ChuaTatToan) {
            throw new BusinessRuleException("Phiếu tạm ứng này đã được giao tiền trước đó (trạng thái ChuaTatToan)");
        }
        if (voucher.getStatus() == AdvanceVoucherStatus.DaTatToan) {
            throw new BusinessRuleException("Phiếu tạm ứng này đã được tất toán (DaTatToan)");
        }
        if (voucher.getStatus() == AdvanceVoucherStatus.DaHuy) {
            throw new BusinessRuleException("Phiếu tạm ứng này đã bị hủy (DaHuy)");
        }

        String treasurerAccountId = currentUserProvider.currentAccountId().orElse("SYSTEM");
        Instant disbursedAt = (request != null && request.disbursedAt() != null) ? request.disbursedAt() : Instant.now();

        voucher.setStatus(AdvanceVoucherStatus.ChuaTatToan);
        voucher.setTreasurerAccountId(treasurerAccountId);
        voucher.setDisbursedAt(disbursedAt);
        if (request != null && request.notes() != null && !request.notes().isBlank()) {
            voucher.setDisbursementNotes(request.notes().trim());
        }

        AdvanceVoucher updated = advanceVoucherRepository.save(voucher);
        log.info("US-46: Thủ quỹ {} xác nhận đã giao tiền phiếu tạm ứng {}, chuyển trạng thái sang ChuaTatToan",
                treasurerAccountId, updated.getAdvanceVoucherId());

        // Thông báo cho người nhận (Category Finance)
        notifyRecipientOnDisbursed(updated, task.taskName());

        return toResponse(updated, task);
    }

    @Override
    @Transactional(readOnly = true)
    public AdvanceVoucherResponse getAdvanceVoucherById(String advanceVoucherId) {
        String clanId = TenantContext.getClanId();
        AdvanceVoucher voucher = advanceVoucherRepository.findById(advanceVoucherId)
                .orElseThrow(() -> new ResourceNotFoundException("Phiếu tạm ứng", "advanceVoucherId", advanceVoucherId));

        if (clanId != null && !clanId.isBlank() && voucher.getClanId() != null && !clanId.equals(voucher.getClanId())) {
            throw new ResourceNotFoundException("Phiếu tạm ứng", "advanceVoucherId", advanceVoucherId);
        }

        EventTaskSummaryDto task = eventQueryService.findTaskSummary(voucher.getTaskId()).orElse(null);
        return toResponse(voucher, task);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdvanceVoucherResponse> searchAdvanceVouchers(String eventId, String taskId, String recipientMemberId, AdvanceVoucherStatus status) {
        String clanId = TenantContext.getClanId();
        List<AdvanceVoucher> list = advanceVoucherRepository.findByFilter(clanId, taskId, recipientMemberId, status);

        return list.stream()
                .map(v -> {
                    EventTaskSummaryDto task = eventQueryService.findTaskSummary(v.getTaskId()).orElse(null);
                    return toResponse(v, task);
                })
                .filter(res -> eventId == null || eventId.isBlank() || eventId.equals(res.eventId()))
                .toList();
    }

    // ------------------------------------------------------------------------- Helpers & Validation

    private void validateBtcAuthorization(String eventId, String clanId) {
        if (currentUserProvider.hasAuthority(SystemRoles.ADMIN)) {
            return;
        }
        if (currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)) {
            return;
        }
        Optional<String> memberIdOpt = currentUserProvider.currentMemberId();
        if (memberIdOpt.isEmpty()) {
            throw new AccessDeniedException("Người dùng chưa liên kết hồ sơ thành viên dòng họ để thực hiện thao tác BTC");
        }
        Set<EventRole> roles = eventQueryService.getMemberRolesInEvent(eventId, memberIdOpt.get());
        boolean hasBtcRole = roles.contains(EventRole.HeadOfOrganizer)
                || roles.contains(EventRole.OrganizerMember)
                || roles.contains(EventRole.EventTreasurer);
        if (!hasBtcRole) {
            throw new AccessDeniedException("Chỉ Ban tổ chức sự kiện mới có quyền lập phiếu tạm ứng kinh phí");
        }
    }

    private void validateTreasurerAuthorization(String eventId, String clanId) {
        if (currentUserProvider.hasAuthority(SystemRoles.ADMIN)) {
            return;
        }
        if (currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)) {
            return;
        }
        Optional<String> memberIdOpt = currentUserProvider.currentMemberId();
        if (memberIdOpt.isEmpty()) {
            throw new AccessDeniedException("Người dùng chưa liên kết hồ sơ thành viên dòng họ để xác nhận giao tiền");
        }
        Set<EventRole> roles = eventQueryService.getMemberRolesInEvent(eventId, memberIdOpt.get());
        if (!roles.contains(EventRole.EventTreasurer)) {
            throw new AccessDeniedException("Chỉ Thủ quỹ sự kiện mới có quyền xác nhận đã giao tiền tạm ứng");
        }
    }

    private void notifyRecipientOnCreated(AdvanceVoucher voucher, String taskName) {
        try {
            identityRoleCommandService.findAccountByMemberId(voucher.getRecipientMemberId()).ifPresent(account ->
                    notificationService.notify(NotificationCommand.builder()
                            .clanId(voucher.getClanId())
                            .recipientAccountId(account.accountId())
                            .category(NotificationCategory.Finance)
                            .title("Phiếu tạm ứng mới đã được lập: " + voucher.getAdvanceVoucherId())
                            .content("Phiếu tạm ứng số tiền " + voucher.getAdvanceAmount().toPlainString() + "đ cho công việc '"
                                    + taskName + "' đã được lập và đang chờ Thủ quỹ chi tiền.")
                            .referenceType("ADVANCE_VOUCHER")
                            .referenceId(voucher.getAdvanceVoucherId())
                            .build()));
        } catch (RuntimeException ex) {
            log.warn("US-46: Không thể gửi thông báo lập phiếu tạm ứng {}: {}", voucher.getAdvanceVoucherId(), ex.getMessage());
        }
    }

    private void notifyRecipientOnDisbursed(AdvanceVoucher voucher, String taskName) {
        try {
            identityRoleCommandService.findAccountByMemberId(voucher.getRecipientMemberId()).ifPresent(account ->
                    notificationService.notify(NotificationCommand.builder()
                            .clanId(voucher.getClanId())
                            .recipientAccountId(account.accountId())
                            .category(NotificationCategory.Finance)
                            .title("Thủ quỹ đã giao tiền tạm ứng: " + voucher.getAdvanceAmount().toPlainString() + "đ")
                            .content("Phiếu tạm ứng " + voucher.getAdvanceVoucherId() + " cho công việc '" + taskName
                                    + "' đã được giao tiền (Chưa tất toán). Hạn nộp chứng từ quyết toán: " + voucher.getSettlementDeadline() + ".")
                            .referenceType("ADVANCE_VOUCHER")
                            .referenceId(voucher.getAdvanceVoucherId())
                            .build()));
        } catch (RuntimeException ex) {
            log.warn("US-46: Không thể gửi thông báo giao tiền tạm ứng {}: {}", voucher.getAdvanceVoucherId(), ex.getMessage());
        }
    }

    private AdvanceVoucherResponse toResponse(AdvanceVoucher v, EventTaskSummaryDto task) {
        String taskName = task != null ? task.taskName() : null;
        String eventId = task != null ? task.eventId() : null;
        String eventName = null;
        if (eventId != null) {
            eventName = eventQueryService.findEventSummary(eventId)
                    .map(EventSummaryDto::eventName)
                    .orElse(null);
        }

        String recipientName = genealogyQueryService.findMemberSummary(v.getRecipientMemberId())
                .map(MemberSummaryDto::fullName)
                .orElse(v.getRecipientMemberId());

        String creatorName = v.getCreatedByAccountId();
        String treasurerName = v.getTreasurerAccountId();

        return new AdvanceVoucherResponse(
                v.getAdvanceVoucherId(),
                v.getTaskId(),
                taskName,
                eventId,
                eventName,
                v.getClanId(),
                v.getRecipientMemberId(),
                recipientName,
                v.getAdvanceAmount(),
                v.getReceivedDate(),
                v.getSettlementDeadline(),
                v.getCommitmentNotes(),
                v.getCreatedByAccountId(),
                creatorName,
                v.getStatus(),
                v.getTreasurerAccountId(),
                treasurerName,
                v.getDisbursedAt(),
                v.getDisbursementNotes(),
                v.getCreatedAt(),
                v.getUpdatedAt()
        );
    }

    private String generateUniqueAdvanceVoucherId() {
        for (int i = 0; i < 5; i++) {
            String candidate = advanceVoucherIdGenerator.generate();
            if (!advanceVoucherRepository.existsById(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Không thể sinh mã phiếu tạm ứng duy nhất sau 5 lần thử");
    }
}
