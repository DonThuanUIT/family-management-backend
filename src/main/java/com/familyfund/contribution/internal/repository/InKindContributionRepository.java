package com.familyfund.contribution.internal.repository;

import com.familyfund.contribution.api.dto.InKindProcessingStatus;
import com.familyfund.contribution.internal.entity.InKindContribution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InKindContributionRepository extends JpaRepository<InKindContribution, String> {

    boolean existsByReceiptNumber(String receiptNumber);

    Optional<InKindContribution> findByReceiptNumber(String receiptNumber);

    @Query("SELECT ikc FROM InKindContribution ikc WHERE ikc.contributionId IN " +
           "(SELECT c.contributionId FROM Contribution c WHERE c.eventId = :eventId) " +
           "AND (:status IS NULL OR ikc.processingStatus = :status)")
    List<InKindContribution> findByEventIdAndStatus(@Param("eventId") String eventId,
                                                    @Param("status") InKindProcessingStatus status);
}
