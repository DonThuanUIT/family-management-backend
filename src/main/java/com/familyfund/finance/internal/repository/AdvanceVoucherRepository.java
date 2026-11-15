package com.familyfund.finance.internal.repository;

import com.familyfund.finance.api.dto.AdvanceVoucherStatus;
import com.familyfund.finance.internal.entity.AdvanceVoucher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdvanceVoucherRepository extends JpaRepository<AdvanceVoucher, String> {

    Optional<AdvanceVoucher> findByAdvanceVoucherIdAndClanId(String advanceVoucherId, String clanId);

    List<AdvanceVoucher> findAllByTaskIdOrderByCreatedAtDesc(String taskId);

    List<AdvanceVoucher> findAllByClanIdOrderByCreatedAtDesc(String clanId);

    List<AdvanceVoucher> findAllByClanIdAndStatusOrderByCreatedAtDesc(String clanId, AdvanceVoucherStatus status);

    @Query("""
            SELECT av FROM AdvanceVoucher av
            WHERE (:clanId IS NULL OR av.clanId IS NULL OR av.clanId = :clanId)
              AND (:taskId IS NULL OR av.taskId = :taskId)
              AND (:recipientMemberId IS NULL OR av.recipientMemberId = :recipientMemberId)
              AND (:status IS NULL OR av.status = :status)
            ORDER BY av.createdAt DESC
            """)
    List<AdvanceVoucher> findByFilter(@Param("clanId") String clanId,
                                      @Param("taskId") String taskId,
                                      @Param("recipientMemberId") String recipientMemberId,
                                      @Param("status") AdvanceVoucherStatus status);

    /**
     * Finds all advance vouchers in ChuaTatToan status whose settlement deadline has passed.
     * Scans cross-clan for background worker execution (US-48).
     */
    @Query("""
            SELECT av FROM AdvanceVoucher av
            WHERE av.status = com.familyfund.finance.api.dto.AdvanceVoucherStatus.ChuaTatToan
              AND av.settlementDeadline < :now
            ORDER BY av.settlementDeadline ASC
            """)
    List<AdvanceVoucher> findOverdueVouchers(@Param("now") java.time.Instant now);

    /**
     * Finds overdue advance vouchers scoped to a specific clan.
     */
    @Query("""
            SELECT av FROM AdvanceVoucher av
            WHERE av.clanId = :clanId
              AND av.status = com.familyfund.finance.api.dto.AdvanceVoucherStatus.ChuaTatToan
              AND av.settlementDeadline < :now
            ORDER BY av.settlementDeadline ASC
            """)
    List<AdvanceVoucher> findOverdueVouchersByClanId(@Param("clanId") String clanId, @Param("now") java.time.Instant now);
}
