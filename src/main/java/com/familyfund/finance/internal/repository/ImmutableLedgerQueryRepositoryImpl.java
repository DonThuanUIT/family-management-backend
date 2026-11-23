package com.familyfund.finance.internal.repository;

import com.familyfund.finance.internal.entity.ImmutableLedger;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Custom Criteria API implementation of {@link ImmutableLedgerQueryRepository} for US-62.
 * Strict read-only operations; exposes zero {@code delete*} methods (US-61 Guard).
 */
@Repository
public class ImmutableLedgerQueryRepositoryImpl implements ImmutableLedgerQueryRepository {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<ImmutableLedger> searchLedgerEntries(LedgerSearchCriteria criteria, Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<ImmutableLedger> cq = cb.createQuery(ImmutableLedger.class);
        Root<ImmutableLedger> root = cq.from(ImmutableLedger.class);
        List<Predicate> predicates = buildPredicates(cb, cq, root, criteria);
        cq.where(predicates.toArray(new Predicate[0]));
        cq.orderBy(buildOrders(cb, root, pageable != null ? pageable.getSort() : Sort.unsorted()));

        List<ImmutableLedger> content;
        if (pageable != null && pageable.isPaged() && pageable.getOffset() > Integer.MAX_VALUE) {
            content = List.of();
        } else {
            TypedQuery<ImmutableLedger> query = entityManager.createQuery(cq);
            if (pageable != null && pageable.isPaged()) {
                query.setFirstResult((int) pageable.getOffset());
                query.setMaxResults(pageable.getPageSize());
            }
            content = query.getResultList();
        }

        CriteriaQuery<Long> countCq = cb.createQuery(Long.class);
        Root<ImmutableLedger> countRoot = countCq.from(ImmutableLedger.class);
        List<Predicate> countPredicates = buildPredicates(cb, countCq, countRoot, criteria);
        countCq.select(cb.count(countRoot));
        countCq.where(countPredicates.toArray(new Predicate[0]));
        Long total = entityManager.createQuery(countCq).getSingleResult();
        long totalElements = total != null ? total : 0L;

        return new PageImpl<>(content, pageable != null ? pageable : Pageable.unpaged(), totalElements);
    }

    @Override
    public LedgerFilteredAggregates aggregateFilteredFlow(LedgerSearchCriteria criteria) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<ImmutableLedger> root = cq.from(ImmutableLedger.class);

        List<Predicate> predicates = buildPredicates(cb, cq, root, criteria);
        Expression<BigDecimal> amountPath = root.get("amount");

        Expression<Long> countExpr = cb.count(root);

        Expression<BigDecimal> inflowCase = cb.<BigDecimal>selectCase()
                .when(cb.greaterThan(amountPath, BigDecimal.ZERO), amountPath)
                .otherwise(BigDecimal.ZERO);
        Expression<BigDecimal> totalInflowExpr = cb.coalesce(cb.sum(inflowCase), BigDecimal.ZERO);

        Expression<BigDecimal> outflowCase = cb.<BigDecimal>selectCase()
                .when(cb.lessThan(amountPath, BigDecimal.ZERO), cb.neg(amountPath))
                .otherwise(BigDecimal.ZERO);
        Expression<BigDecimal> totalOutflowExpr = cb.coalesce(cb.sum(outflowCase), BigDecimal.ZERO);

        cq.multiselect(
                countExpr.alias("totalCount"),
                totalInflowExpr.alias("totalInflow"),
                totalOutflowExpr.alias("totalOutflow")
        );
        cq.where(predicates.toArray(new Predicate[0]));

        Tuple tuple = entityManager.createQuery(cq).getSingleResult();
        long totalCount = tuple.get("totalCount", Long.class) != null ? tuple.get("totalCount", Long.class) : 0L;
        BigDecimal rawInflow = tuple.get("totalInflow", BigDecimal.class);
        BigDecimal rawOutflow = tuple.get("totalOutflow", BigDecimal.class);

        BigDecimal totalInflow = rawInflow != null ? rawInflow.setScale(2, RoundingMode.HALF_UP) : ZERO;
        BigDecimal totalOutflow = rawOutflow != null ? rawOutflow.setScale(2, RoundingMode.HALF_UP) : ZERO;
        BigDecimal netAmount = totalInflow.subtract(totalOutflow).setScale(2, RoundingMode.HALF_UP);

        return new LedgerFilteredAggregates(totalCount, totalInflow, totalOutflow, netAmount);
    }

    private List<Predicate> buildPredicates(
            CriteriaBuilder cb,
            CriteriaQuery<?> query,
            Root<ImmutableLedger> root,
            LedgerSearchCriteria criteria
    ) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(root.get("clanId"), criteria.clanId()));

        if (criteria.fromInstant() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), criteria.fromInstant()));
        }
        if (criteria.toInstant() != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), criteria.toInstant()));
        }
        if (criteria.transactionTypes() != null && !criteria.transactionTypes().isEmpty()) {
            predicates.add(root.get("transactionType").in(criteria.transactionTypes()));
        }
        if (criteria.eventId() != null && !criteria.eventId().isBlank()) {
            predicates.add(cb.equal(root.get("eventId"), criteria.eventId().trim()));
        }
        if (criteria.keyword() != null && !criteria.keyword().isBlank()) {
            String escaped = escapeLikePattern(criteria.keyword().trim().toLowerCase(Locale.ROOT));
            String kw = "%" + escaped + "%";
            Predicate txIdLike = cb.like(cb.lower(root.get("transactionId")), kw, '\\');
            Predicate descLike = cb.like(cb.lower(root.get("description")), kw, '\\');
            predicates.add(cb.or(txIdLike, descLike));
        }
        if (criteria.onlyWithAdjustments() != null) {
            Subquery<String> sub = query.subquery(String.class);
            Root<ImmutableLedger> subRoot = sub.from(ImmutableLedger.class);
            sub.select(subRoot.get("transactionId"))
                    .where(
                            cb.equal(subRoot.get("clanId"), root.get("clanId")),
                            cb.equal(subRoot.get("originalTransactionId"), root.get("transactionId"))
                    );
            if (Boolean.TRUE.equals(criteria.onlyWithAdjustments())) {
                predicates.add(cb.or(cb.isNotNull(root.get("originalTransactionId")), cb.exists(sub)));
            } else {
                predicates.add(cb.and(cb.isNull(root.get("originalTransactionId")), cb.not(cb.exists(sub))));
            }
        }
        return predicates;
    }

    private List<Order> buildOrders(CriteriaBuilder cb, Root<ImmutableLedger> root, Sort sort) {
        List<Order> orders = new ArrayList<>();
        if (sort != null && sort.isSorted()) {
            for (Sort.Order sortOrder : sort) {
                String property = sortOrder.getProperty();
                if (sortOrder.isAscending()) {
                    orders.add(cb.asc(root.get(property)));
                } else {
                    orders.add(cb.desc(root.get(property)));
                }
            }
        }
        if (orders.isEmpty()) {
            orders.add(cb.desc(root.get("createdAt")));
            orders.add(cb.desc(root.get("transactionId")));
        }
        return orders;
    }

    private static String escapeLikePattern(String input) {
        return input
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
