package com.familyfund.finance.internal.repository;

import com.familyfund.finance.internal.entity.ImmutableLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ImmutableLedgerRepository extends JpaRepository<ImmutableLedger, String> {

    Optional<ImmutableLedger> findByTransactionIdAndClanId(String transactionId, String clanId);

    List<ImmutableLedger> findByClanIdOrderByCreatedAtDesc(String clanId);
}
