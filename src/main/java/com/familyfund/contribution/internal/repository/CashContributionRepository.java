package com.familyfund.contribution.internal.repository;

import com.familyfund.contribution.internal.entity.CashContribution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CashContributionRepository extends JpaRepository<CashContribution, String> {

    boolean existsByReceiptNumber(String receiptNumber);

    Optional<CashContribution> findByReceiptNumber(String receiptNumber);

    @Query("SELECT c, cc FROM Contribution c, CashContribution cc " +
           "WHERE c.contributionId = cc.contributionId " +
           "AND cc.paymentMethod = com.familyfund.contribution.api.dto.PaymentMethod.BankTransfer " +
           "AND (cc.isReconciled IS NULL OR cc.isReconciled = false) " +
           "AND (:eventId IS NULL OR c.eventId = :eventId) " +
           "ORDER BY c.contributionDate DESC")
    List<Object[]> findUnreconciledBankTransfers(@Param("eventId") String eventId);

    Optional<CashContribution> findByBankTransactionCode(String bankTransactionCode);
}
