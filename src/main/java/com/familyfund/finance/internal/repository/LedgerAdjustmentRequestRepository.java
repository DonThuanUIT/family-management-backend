package com.familyfund.finance.internal.repository;

import com.familyfund.finance.api.dto.LedgerAdjustmentStatus;
import com.familyfund.finance.internal.entity.LedgerAdjustmentRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface LedgerAdjustmentRequestRepository extends JpaRepository<LedgerAdjustmentRequest, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM LedgerAdjustmentRequest r WHERE r.requestId = :requestId")
    Optional<LedgerAdjustmentRequest> findByIdForUpdate(@Param("requestId") String requestId);

    boolean existsByOriginalTransactionIdAndApprovalStatus(
            String originalTransactionId,
            LedgerAdjustmentStatus approvalStatus
    );

    List<LedgerAdjustmentRequest> findByOriginalTransactionIdOrderByCreatedAtDesc(String originalTransactionId);

    List<LedgerAdjustmentRequest> findByOriginalTransactionIdInAndApprovalStatus(
            Collection<String> originalTransactionIds,
            LedgerAdjustmentStatus approvalStatus
    );

    @Query("""
            SELECT COUNT(r) FROM LedgerAdjustmentRequest r
            JOIN ImmutableLedger l ON r.originalTransactionId = l.transactionId
            WHERE l.clanId = :clanId
              AND r.approvalStatus = com.familyfund.finance.api.dto.LedgerAdjustmentStatus.Pending
            """)
    long countPendingByClanId(@Param("clanId") String clanId);

    @Query("""
            SELECT r FROM LedgerAdjustmentRequest r
            JOIN ImmutableLedger l ON r.originalTransactionId = l.transactionId
            WHERE (:clanId IS NULL OR l.clanId = :clanId)
              AND (:originalTransactionId IS NULL OR r.originalTransactionId = :originalTransactionId)
              AND (:status IS NULL OR r.approvalStatus = :status)
            ORDER BY r.createdAt DESC
            """)
    List<LedgerAdjustmentRequest> findByFilter(
            @Param("clanId") String clanId,
            @Param("originalTransactionId") String originalTransactionId,
            @Param("status") LedgerAdjustmentStatus status
    );
}
