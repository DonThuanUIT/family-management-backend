package com.familyfund.finance.internal.entity;

import com.familyfund.core.exception.LedgerImmutabilityViolationException;
import com.familyfund.finance.api.dto.LedgerTransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Append-only entity mapping table {@code immutable_ledgers} (NFR-01 / US-13 / US-15).
 */
@Entity
@Immutable
@Table(name = "immutable_ledgers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImmutableLedger {

    @Id
    @Column(name = "transaction_id", length = 20, nullable = false, updatable = false)
    private String transactionId;

    @Column(name = "clan_id", length = 20, nullable = false, updatable = false)
    private String clanId;

    @Column(name = "event_id", length = 20, updatable = false)
    private String eventId;

    @Column(name = "adjustment_request_id", length = 20, unique = true, updatable = false)
    private String adjustmentRequestId;

    @Column(name = "disbursement_request_id", length = 20, unique = true, updatable = false)
    private String disbursementRequestId;

    @Column(name = "original_transaction_id", length = 20, updatable = false)
    private String originalTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", length = 30, nullable = false, updatable = false)
    private LedgerTransactionType transactionType;

    @Column(name = "amount", precision = 18, scale = 2, nullable = false, updatable = false)
    private BigDecimal amount;

    @Column(name = "running_balance", precision = 18, scale = 2, nullable = false, updatable = false)
    private BigDecimal runningBalance;

    @Column(name = "description", columnDefinition = "TEXT", nullable = false, updatable = false)
    private String description;

    @Column(name = "created_by_account_id", length = 20, nullable = false, updatable = false)
    private String createdByAccountId;

    @Column(name = "approved_by_account_id", length = 20, nullable = false, updatable = false)
    private String approvedByAccountId;

    @Column(name = "previous_hash", length = 64, nullable = false, updatable = false)
    private String previousHash;

    @Column(name = "record_hash", length = 64, unique = true, nullable = false, updatable = false)
    private String recordHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdateAttempt() {
        throw new LedgerImmutabilityViolationException(
                "Vi phạm bảo mật sổ cái bất biến (NFR-01): Cấm cập nhật (UPDATE) bản ghi sổ cái " + transactionId);
    }

    @PreRemove
    protected void onDeleteAttempt() {
        throw new LedgerImmutabilityViolationException(
                "Vi phạm bảo mật sổ cái bất biến (NFR-01): Cấm xóa (DELETE) bản ghi sổ cái " + transactionId);
    }
}
