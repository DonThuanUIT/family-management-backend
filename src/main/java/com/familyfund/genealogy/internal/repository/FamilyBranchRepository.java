package com.familyfund.genealogy.internal.repository;

import com.familyfund.genealogy.internal.entity.FamilyBranch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FamilyBranchRepository extends JpaRepository<FamilyBranch, String> {

    /**
     * Checks if a family branch exists by ID.
     */
    boolean existsByBranchId(String branchId);

    /**
     * Finds child branches of a parent branch.
     */
    List<FamilyBranch> findAllByParentBranchIdOrderByBranchNameAsc(String parentBranchId);
}

