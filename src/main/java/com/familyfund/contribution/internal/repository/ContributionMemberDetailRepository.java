package com.familyfund.contribution.internal.repository;

import com.familyfund.contribution.internal.entity.ContributionMemberDetail;
import com.familyfund.contribution.internal.entity.ContributionMemberDetailId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface ContributionMemberDetailRepository extends JpaRepository<ContributionMemberDetail, ContributionMemberDetailId> {

    List<ContributionMemberDetail> findAllByContributionId(String contributionId);

    List<ContributionMemberDetail> findAllByContributionIdIn(List<String> contributionIds);

    List<ContributionMemberDetail> findAllByMemberId(String memberId);

    void deleteAllByContributionId(String contributionId);

    /**
     * Projection chứa số liệu đóng góp trực tiếp của từng nhánh trong cây con
     * được gom nhóm từ bảng {@code contribution_member_details} qua PostgreSQL Recursive CTE (US-11).
     */
    interface BranchDirectContributionProjection {
        String getBranchId();
        BigDecimal getDirectTotalAmount();
        BigDecimal getDirectCashAmount();
        BigDecimal getDirectInKindAmount();
        Integer getContributingMemberCount();
        Integer getContributionCount();
    }

    /**
     * Truy vấn PostgreSQL Recursive CTE kết hợp gom nhóm dữ liệu đóng góp trực tiếp của từng nhánh
     * trong toàn bộ cây nhánh con bắt đầu từ {@code rootBranchId}.
     * <ul>
     *   <li>Lọc theo {@code clanId} (Multi-tenancy isolation).</li>
     *   <li>Lọc theo {@code eventId} ({@code maSuKien}) nếu được truyền vào.</li>
     *   <li>Lọc theo {@code year} (năm đóng góp) nếu được truyền vào.</li>
     *   <li>Loại trừ các hiện vật cho mượn đã hoàn trả gia chủ ({@code processing_status = 'Returned'}).</li>
     * </ul>
     */
    @Query(value = """
            WITH RECURSIVE branch_subtree AS (
                SELECT 
                    anchor.branch_id,
                    anchor.parent_branch_id,
                    anchor.clan_id,
                    0 AS relative_depth,
                    ARRAY[CAST(anchor.branch_id AS text)] AS visited_path
                FROM family_branches anchor
                WHERE anchor.clan_id = :clanId
                  AND anchor.branch_id = :rootBranchId

                UNION ALL

                SELECT 
                    child.branch_id,
                    child.parent_branch_id,
                    child.clan_id,
                    parent_tree.relative_depth + 1 AS relative_depth,
                    parent_tree.visited_path || CAST(child.branch_id AS text)
                FROM family_branches child
                INNER JOIN branch_subtree parent_tree
                    ON child.parent_branch_id = parent_tree.branch_id
                   AND child.clan_id = parent_tree.clan_id
                WHERE NOT (CAST(child.branch_id AS text) = ANY(parent_tree.visited_path))
            ),
            eligible_contributions AS (
                SELECT 
                    cmd.member_id,
                    cmd.contribution_id,
                    cmd.recorded_amount,
                    c.contribution_type
                FROM contribution_member_details cmd
                INNER JOIN contributions c 
                    ON c.contribution_id = cmd.contribution_id
                INNER JOIN events e 
                    ON e.event_id = c.event_id
                LEFT JOIN in_kind_contributions ikc 
                    ON ikc.contribution_id = c.contribution_id
                WHERE e.clan_id = :clanId
                  AND (CAST(:eventId AS text) IS NULL OR c.event_id = CAST(:eventId AS text))
                  AND (CAST(:year AS integer) IS NULL OR EXTRACT(YEAR FROM c.contribution_date AT TIME ZONE 'UTC') = CAST(:year AS integer))
                  AND (c.contribution_type <> 'InKind' OR ikc.processing_status IS NULL OR ikc.processing_status <> 'Returned')
            )
            SELECT 
                bt.branch_id AS branchId,
                COALESCE(SUM(ec.recorded_amount), 0) AS directTotalAmount,
                COALESCE(SUM(CASE WHEN ec.contribution_type = 'Cash' THEN ec.recorded_amount ELSE 0 END), 0) AS directCashAmount,
                COALESCE(SUM(CASE WHEN ec.contribution_type = 'InKind' THEN ec.recorded_amount ELSE 0 END), 0) AS directInKindAmount,
                CAST(COUNT(DISTINCT ec.member_id) AS integer) AS contributingMemberCount,
                CAST(COUNT(DISTINCT ec.contribution_id) AS integer) AS contributionCount
            FROM branch_subtree bt
            LEFT JOIN members m 
                ON m.branch_id = bt.branch_id
            LEFT JOIN eligible_contributions ec 
                ON ec.member_id = m.member_id
            GROUP BY bt.branch_id, bt.relative_depth
            ORDER BY bt.relative_depth ASC, bt.branch_id ASC
            """, nativeQuery = true)
    List<BranchDirectContributionProjection> aggregateDirectContributionsByBranchSubtree(
            @Param("clanId") String clanId,
            @Param("rootBranchId") String rootBranchId,
            @Param("eventId") String eventId,
            @Param("year") Integer year
    );
}
