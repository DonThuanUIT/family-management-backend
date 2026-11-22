package com.familyfund.finance.internal.repository;

import com.familyfund.finance.api.dto.CommonFundDisbursementStatus;
import com.familyfund.finance.api.dto.CommonFundExpenseCategory;
import com.familyfund.finance.internal.entity.CommonFundDisbursementRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface CommonFundDisbursementRequestRepository extends JpaRepository<CommonFundDisbursementRequest, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM CommonFundDisbursementRequest r WHERE r.requestId = :requestId")
    Optional<CommonFundDisbursementRequest> findByIdForUpdate(@Param("requestId") String requestId);

    @Query("""
            SELECT r FROM CommonFundDisbursementRequest r
            WHERE (:clanId IS NULL OR r.clanId = :clanId)
              AND (:status IS NULL OR r.approvalStatus = :status)
              AND (:category IS NULL OR r.category = :category)
            ORDER BY r.createdAt DESC
            """)
    List<CommonFundDisbursementRequest> findByFilter(
            @Param("clanId") String clanId,
            @Param("status") CommonFundDisbursementStatus status,
            @Param("category") CommonFundExpenseCategory category
    );

    @Query("""
            SELECT COALESCE(SUM(r.amount), 0)
            FROM CommonFundDisbursementRequest r
            WHERE r.clanId = :clanId
              AND r.approvalStatus = com.familyfund.finance.api.dto.CommonFundDisbursementStatus.Pending
            """)
    BigDecimal sumPendingAmountByClanId(@Param("clanId") String clanId);
}
