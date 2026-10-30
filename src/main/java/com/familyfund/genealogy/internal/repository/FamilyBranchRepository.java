package com.familyfund.genealogy.internal.repository;

import com.familyfund.genealogy.internal.entity.FamilyBranch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    // BRANCH_HEAD scope: the caller's member profile is the representative (Trưởng chi) of this branch
    boolean existsByClanIdAndBranchIdAndRepresentative_MemberId(String clanId, String branchId, String representativeMemberId);

    List<FamilyBranch> findByClanIdAndParentBranch_BranchIdOrderByBranchNameAsc(String clanId, String parentBranchId);

    List<FamilyBranch> findByClanIdAndRepresentative_MemberId(String clanId, String representativeId);

    @Query("SELECT fb FROM FamilyBranch fb LEFT JOIN FETCH fb.representative WHERE fb.clanId = :clanId ORDER BY fb.generationLevel ASC, fb.branchName ASC")
    List<FamilyBranch> findAllByClanIdWithRepresentative(@Param("clanId") String clanId);
}
