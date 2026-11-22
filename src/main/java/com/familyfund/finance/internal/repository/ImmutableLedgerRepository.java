package com.familyfund.finance.internal.repository;

import com.familyfund.finance.internal.entity.ImmutableLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ImmutableLedgerRepository extends JpaRepository<ImmutableLedger, String> {

    @Query(
            value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext('immutable_ledger:' || :clanId))) AS ledger_lock",
            nativeQuery = true
    )
    Integer acquireClanLedgerLock(@Param("clanId") String clanId);

    Optional<ImmutableLedger> findFirstByClanIdOrderByCreatedAtDescTransactionIdDesc(String clanId);

    Optional<ImmutableLedger> findByDisbursementRequestId(String disbursementRequestId);

    List<ImmutableLedger> findByDisbursementRequestIdIn(Collection<String> disbursementRequestIds);

    Optional<ImmutableLedger> findByTransactionIdAndClanId(String transactionId, String clanId);

    List<ImmutableLedger> findByClanIdOrderByCreatedAtDesc(String clanId);
}
