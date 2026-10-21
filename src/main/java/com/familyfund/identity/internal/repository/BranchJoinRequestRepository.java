package com.familyfund.identity.internal.repository;

import com.familyfund.identity.internal.entity.BranchJoinRequest;
import com.familyfund.identity.internal.entity.JoinRequestApprovalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;

/**
 * Spring Data JPA Repository for `BranchJoinRequest`.
 */
@Repository
public interface BranchJoinRequestRepository extends JpaRepository<BranchJoinRequest, String> {

    @Query("SELECT r FROM BranchJoinRequest r JOIN FETCH r.userAccount WHERE r.requestId = :requestId AND r.clanId = :clanId")
    Optional<BranchJoinRequest> findWithAccountByRequestIdAndClanId(
            @Param("requestId") String requestId,
            @Param("clanId") String clanId
    );

    @Query(value = "SELECT r FROM BranchJoinRequest r JOIN FETCH r.userAccount WHERE r.clanId = :clanId AND r.approvalStatus = :status",
           countQuery = "SELECT COUNT(r) FROM BranchJoinRequest r WHERE r.clanId = :clanId AND r.approvalStatus = :status")
    Page<BranchJoinRequest> findWithAccountByClanIdAndApprovalStatus(
            @Param("clanId") String clanId,
            @Param("status") JoinRequestApprovalStatus status,
            Pageable pageable
    );

    @Query(value = "SELECT r FROM BranchJoinRequest r JOIN FETCH r.userAccount WHERE r.clanId = :clanId AND r.targetBranchId IN :branchIds AND r.approvalStatus = :status",
           countQuery = "SELECT COUNT(r) FROM BranchJoinRequest r WHERE r.clanId = :clanId AND r.targetBranchId IN :branchIds AND r.approvalStatus = :status")
    Page<BranchJoinRequest> findWithAccountByClanIdAndBranchIdsAndApprovalStatus(
            @Param("clanId") String clanId,
            @Param("branchIds") Collection<String> branchIds,
            @Param("status") JoinRequestApprovalStatus status,
            Pageable pageable
    );

    boolean existsByUserAccountAccountIdAndApprovalStatus(String accountId, JoinRequestApprovalStatus status);
}
