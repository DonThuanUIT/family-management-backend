package com.familyfund.finance.internal.repository;

import com.familyfund.finance.internal.entity.ImmutableLedger;

/**
 * Custom write fragment for {@link ImmutableLedgerRepository} enforcing append-only INSERT semantics
 * and blocking overwrite attempts (NFR-01 / US-61).
 */
public interface ImmutableLedgerWriteGuard {

    ImmutableLedger save(ImmutableLedger entity);

    ImmutableLedger saveAndFlush(ImmutableLedger entity);
}
