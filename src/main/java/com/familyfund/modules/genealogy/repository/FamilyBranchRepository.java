package com.familyfund.modules.genealogy.repository;

import com.familyfund.modules.genealogy.entity.FamilyBranch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FamilyBranchRepository extends JpaRepository<FamilyBranch, String> {

    Optional<FamilyBranch> findByParentBranchIsNull();

    List<FamilyBranch> findByParentBranch_BranchId(String parentBranchId);

    boolean existsByParentBranch_BranchId(String parentBranchId);
}
