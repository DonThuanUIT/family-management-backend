package com.familyfund.finance.internal.repository;

import com.familyfund.finance.internal.entity.ImmutableLedger;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Append-only Spring Data repository for {@link ImmutableLedger} (NFR-01 / US-61).
 * Intentionally extends {@link Repository} rather than {@code JpaRepository} / {@code CrudRepository}
 * so no {@code delete*} methods are exposed at compile time.
 */
@org.springframework.stereotype.Repository
public interface ImmutableLedgerRepository extends Repository<ImmutableLedger, String>, ImmutableLedgerWriteGuard {

    Optional<ImmutableLedger> findById(String transactionId);

    List<ImmutableLedger> findAllById(Iterable<String> transactionIds);

    boolean existsById(String transactionId);

    long count();

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

    List<ImmutableLedger> findByClanIdOrderByCreatedAtAscTransactionIdAsc(String clanId);
}
