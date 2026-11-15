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
}
