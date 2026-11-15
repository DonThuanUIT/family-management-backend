package com.familyfund.finance.internal.service;

import com.familyfund.event.api.EventQueryService;
import com.familyfund.event.api.dto.EventTaskSummaryDto;
import com.familyfund.finance.api.AdvanceVoucherQueryService;
import com.familyfund.finance.api.dto.AdvanceVoucherDto;
import com.familyfund.finance.internal.entity.AdvanceVoucher;
import com.familyfund.finance.internal.repository.AdvanceVoucherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AdvanceVoucherQueryServiceImpl implements AdvanceVoucherQueryService {

    private final AdvanceVoucherRepository advanceVoucherRepository;
    private final EventQueryService eventQueryService;

    @Override
    @Transactional(readOnly = true)
    public Optional<AdvanceVoucherDto> findById(String advanceVoucherId) {
        return advanceVoucherRepository.findById(advanceVoucherId)
                .map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdvanceVoucherDto> findByTaskId(String taskId) {
        return advanceVoucherRepository.findAllByTaskIdOrderByCreatedAtDesc(taskId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdvanceVoucherDto> findByEventId(String eventId) {
        return advanceVoucherRepository.findAll().stream()
                .filter(v -> {
                    Optional<EventTaskSummaryDto> task = eventQueryService.findTaskSummary(v.getTaskId());
                    return task.isPresent() && eventId.equals(task.get().eventId());
                })
                .map(this::toDto)
                .toList();
    }

    private AdvanceVoucherDto toDto(AdvanceVoucher v) {
        String eventId = eventQueryService.findTaskSummary(v.getTaskId())
                .map(EventTaskSummaryDto::eventId)
                .orElse(null);

        return new AdvanceVoucherDto(
                v.getAdvanceVoucherId(),
                v.getTaskId(),
                eventId,
                v.getClanId(),
                v.getRecipientMemberId(),
                v.getAdvanceAmount(),
                v.getReceivedDate(),
                v.getSettlementDeadline(),
                v.getCommitmentNotes(),
                v.getCreatedByAccountId(),
                v.getStatus(),
                v.getTreasurerAccountId(),
                v.getDisbursedAt(),
                v.getDisbursementNotes(),
                v.getOverdueAt(),
                v.getCreatedAt()
        );
    }
}
