package com.familyfund.finance.internal.repository;

import com.familyfund.finance.internal.entity.ImmutableLedger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Read-only dynamic query fragment for {@link ImmutableLedger} (US-62).
 * Note: Must never expose any method starting with {@code delete} (NFR-01 / US-61).
 */
public interface ImmutableLedgerQueryRepository {

    Page<ImmutableLedger> searchLedgerEntries(LedgerSearchCriteria criteria, Pageable pageable);

    LedgerFilteredAggregates aggregateFilteredFlow(LedgerSearchCriteria criteria);
}
