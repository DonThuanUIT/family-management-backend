package com.familyfund.contribution.internal.repository;

import com.familyfund.contribution.internal.entity.Contribution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContributionRepository extends JpaRepository<Contribution, String> {

    List<Contribution> findByEventId(String eventId);

    List<Contribution> findByPayerMemberId(String payerMemberId);
}
