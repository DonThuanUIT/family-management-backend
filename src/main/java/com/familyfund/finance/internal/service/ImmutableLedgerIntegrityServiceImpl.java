package com.familyfund.finance.internal.service;

import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.finance.internal.dto.response.LedgerIntegrityReportResponse;
import com.familyfund.finance.internal.entity.ImmutableLedger;
import com.familyfund.finance.internal.repository.ImmutableLedgerRepository;
import com.familyfund.finance.internal.util.ImmutableLedgerHashCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Cryptographic audit service validating the SHA-256 blockchain-style hash chain
 * of {@code immutable_ledgers} per clan (NFR-01 / US-61).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImmutableLedgerIntegrityServiceImpl implements ImmutableLedgerIntegrityService {

    private final ImmutableLedgerRepository immutableLedgerRepository;
    private final ImmutableLedgerHashCalculator ledgerHashCalculator;

    @Override
    @Transactional(readOnly = true)
    public LedgerIntegrityReportResponse verifyCurrentClanChain() {
        String rawClanId = TenantContext.getClanId();
        if (rawClanId == null || rawClanId.isBlank()) {
            throw new BusinessRuleException("Thiếu ngữ cảnh dòng họ (clanId) để kiểm toán tính toàn vẹn Sổ cái");
        }
        String clanId = rawClanId.trim();

        List<ImmutableLedger> orderedEntries =
                immutableLedgerRepository.findByClanIdOrderByCreatedAtAscTransactionIdAsc(clanId);

        ImmutableLedgerHashCalculator.ChainVerificationResult result =
                ledgerHashCalculator.verifyChain(orderedEntries);

        ImmutableLedger latest = orderedEntries.isEmpty()
                ? null
                : orderedEntries.get(orderedEntries.size() - 1);

        List<LedgerIntegrityReportResponse.BrokenLinkResponse> brokenResponses = result.brokenLinks().stream()
                .map(LedgerIntegrityReportResponse.BrokenLinkResponse::from)
                .toList();

        if (!result.valid()) {
            log.error("SECURITY VIOLATION [NFR-01]: Phát hiện {} điểm gãy chuỗi SHA-256 trên Sổ cái dòng họ {}",
                    brokenResponses.size(), clanId);
        }

        return new LedgerIntegrityReportResponse(
                clanId,
                result.valid(),
                result.totalEntries(),
                result.verifiedEntries(),
                ImmutableLedgerHashCalculator.GENESIS_HASH,
                latest != null ? latest.getTransactionId() : null,
                latest != null ? latest.getRecordHash() : null,
                brokenResponses,
                Instant.now()
        );
    }
}
