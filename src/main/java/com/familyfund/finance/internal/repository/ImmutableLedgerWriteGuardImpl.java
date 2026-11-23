package com.familyfund.finance.internal.repository;

import com.familyfund.core.exception.LedgerImmutabilityViolationException;
import com.familyfund.finance.internal.entity.ImmutableLedger;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.transaction.annotation.Transactional;

/**
 * Append-only persistence guard for {@link ImmutableLedger}.
 * Rejects any attempt to overwrite an existing transaction and always uses {@code persist()}
 * rather than {@code merge()} so JPA never emits SQL {@code UPDATE}.
 */
public class ImmutableLedgerWriteGuardImpl implements ImmutableLedgerWriteGuard {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public ImmutableLedger save(ImmutableLedger entity) {
        if (entity == null) {
            throw new IllegalArgumentException("ImmutableLedger entity must not be null");
        }
        String txId = entity.getTransactionId();
        if (txId != null && entityManager.find(ImmutableLedger.class, txId) != null) {
            throw new LedgerImmutabilityViolationException(
                    "Vi phạm bảo mật sổ cái bất biến (NFR-01): Giao dịch sổ cái " + txId
                            + " đã tồn tại, cấm ghi đè (UPDATE).");
        }
        entityManager.persist(entity);
        return entity;
    }

    @Override
    @Transactional
    public ImmutableLedger saveAndFlush(ImmutableLedger entity) {
        ImmutableLedger persisted = save(entity);
        entityManager.flush();
        return persisted;
    }
}
