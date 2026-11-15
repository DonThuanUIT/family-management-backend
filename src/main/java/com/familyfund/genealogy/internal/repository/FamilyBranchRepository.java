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

    Optional<FamilyBranch> findByClanIdAndParentBranchIsNull(String clanId);

    Optional<FamilyBranch> findFirstByClanIdAndParentBranchIsNullOrderByGenerationLevelAscBranchIdAsc(String clanId);

    List<FamilyBranch> findByClanIdAndParentBranch_BranchId(String clanId, String parentBranchId);

    List<FamilyBranch> findByClanIdOrderByGenerationLevelAsc(String clanId);

    List<FamilyBranch> findByClanIdAndGenerationLevel(String clanId, Integer generationLevel);

    boolean existsByClanIdAndParentBranch_BranchId(String clanId, String parentBranchId);

    Optional<FamilyBranch> findByClanIdAndBranchId(String clanId, String branchId);

    boolean existsByClanIdAndBranchId(String clanId, String branchId);

    // BRANCH_HEAD scope: the caller's member profile is the representative (Branch Head) of this branch
    boolean existsByClanIdAndBranchIdAndRepresentative_MemberId(String clanId, String branchId, String representativeMemberId);

    List<FamilyBranch> findByClanIdAndParentBranch_BranchIdOrderByBranchNameAsc(String clanId, String parentBranchId);

    List<FamilyBranch> findByClanIdAndRepresentative_MemberId(String clanId, String representativeId);

    @Query("SELECT fb FROM FamilyBranch fb LEFT JOIN FETCH fb.representative WHERE fb.clanId = :clanId ORDER BY fb.generationLevel ASC, fb.branchName ASC")
    List<FamilyBranch> findAllByClanIdWithRepresentative(@Param("clanId") String clanId);

    /**
     * Truy vấn PostgreSQL Recursive CTE lấy toàn bộ cây nhánh con bắt đầu từ {@code rootBranchId}
     * trong phạm vi {@code clanId}, có kiểm soát chống chu trình (Cycle detection via path array).
     */
    @Query(value = """
            WITH RECURSIVE branch_subtree AS (
                SELECT 
                    anchor.branch_id,
                    anchor.clan_id,
                    0 AS relative_depth,
                    ARRAY[CAST(anchor.branch_id AS text)] AS visited_path
                FROM family_branches anchor
                WHERE anchor.clan_id = :clanId
                  AND anchor.branch_id = :rootBranchId

                UNION ALL

                SELECT 
                    child.branch_id,
                    child.clan_id,
                    parent_tree.relative_depth + 1 AS relative_depth,
                    parent_tree.visited_path || CAST(child.branch_id AS text)
                FROM family_branches child
                INNER JOIN branch_subtree parent_tree
                    ON child.parent_branch_id = parent_tree.branch_id
                   AND child.clan_id = parent_tree.clan_id
                WHERE NOT (CAST(child.branch_id AS text) = ANY(parent_tree.visited_path))
            )
            SELECT fb.*
            FROM branch_subtree bs
            INNER JOIN family_branches fb ON fb.branch_id = bs.branch_id
            ORDER BY bs.relative_depth ASC, fb.generation_level ASC, fb.branch_id ASC
            """, nativeQuery = true)
    List<FamilyBranch> findSubtreeByClanIdAndRootBranchId(
            @Param("clanId") String clanId,
            @Param("rootBranchId") String rootBranchId
    );
}
