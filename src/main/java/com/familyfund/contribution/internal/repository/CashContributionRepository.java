package com.familyfund.contribution.internal.repository;

import com.familyfund.contribution.internal.entity.CashContribution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CashContributionRepository extends JpaRepository<CashContribution, String> {

    boolean existsByReceiptNumber(String receiptNumber);

    Optional<CashContribution> findByReceiptNumber(String receiptNumber);
}
