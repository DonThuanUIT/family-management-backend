package com.familyfund.genealogy.internal.repository;

import com.familyfund.genealogy.internal.entity.FamilyBranch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FamilyBranchRepository extends JpaRepository<FamilyBranch, String> {

    // Multi-tenant Scoped Queries
    Optional<FamilyBranch> findByClanIdAndParentBranchIsNull(String clanId);

    List<FamilyBranch> findByClanIdAndParentBranch_BranchId(String clanId, String parentBranchId);

    boolean existsByClanIdAndParentBranch_BranchId(String clanId, String parentBranchId);

    Optional<FamilyBranch> findByClanIdAndBranchId(String clanId, String branchId);

    boolean existsByClanIdAndBranchId(String clanId, String branchId);

    List<FamilyBranch> findByClanIdAndParentBranch_BranchIdOrderByBranchNameAsc(String clanId, String parentBranchId);

    // Legacy queries for backward compatibility
    Optional<FamilyBranch> findByParentBranchIsNull();

    List<FamilyBranch> findByParentBranch_BranchId(String parentBranchId);

    boolean existsByParentBranch_BranchId(String parentBranchId);

    boolean existsByBranchId(String branchId);

    List<FamilyBranch> findByParentBranch_BranchIdOrderByBranchNameAsc(String parentBranchId);
}
