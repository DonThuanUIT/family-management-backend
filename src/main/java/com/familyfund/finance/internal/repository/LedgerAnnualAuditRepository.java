package com.familyfund.finance.internal.repository;

import com.familyfund.finance.api.dto.LedgerAnnualAuditStatus;
import com.familyfund.finance.internal.entity.LedgerAnnualAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link LedgerAnnualAudit} records storing annual common-fund reconciliations.
 */
@Repository
public interface LedgerAnnualAuditRepository extends JpaRepository<LedgerAnnualAudit, String> {

    boolean existsByClanIdAndAuditYearAndAuditStatus(
            String clanId,
            Integer auditYear,
            LedgerAnnualAuditStatus auditStatus
    );

    Optional<LedgerAnnualAudit> findByAuditIdAndClanId(String auditId, String clanId);

    @Query("""
            SELECT a FROM LedgerAnnualAudit a
            WHERE a.clanId = :clanId
              AND (:auditYear IS NULL OR a.auditYear = :auditYear)
              AND (:status IS NULL OR a.auditStatus = :status)
            ORDER BY a.auditYear DESC,
                     CASE WHEN a.auditStatus = com.familyfund.finance.api.dto.LedgerAnnualAuditStatus.Matched THEN 0 ELSE 1 END ASC,
                     a.createdAt DESC,
                     a.auditId DESC
            """)
    List<LedgerAnnualAudit> findByFilter(
            @Param("clanId") String clanId,
            @Param("auditYear") Integer auditYear,
            @Param("status") LedgerAnnualAuditStatus status
    );
}
