package com.familyfund.genealogy.internal.repository;

import com.familyfund.genealogy.internal.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MemberRepository extends JpaRepository<Member, String> {

    // Global uniqueness guard for MemberIdGenerator: members.member_id is a clan-agnostic PRIMARY KEY
    boolean existsByMemberId(String memberId);

    @Query("SELECT m.memberId FROM Member m WHERE m.memberId IN :memberIds")
    List<String> findExistingMemberIds(@Param("memberIds") java.util.Collection<String> memberIds);

    // Multi-tenant scoped queries
    Optional<Member> findByBranch_ClanIdAndMemberId(String clanId, String memberId);

    boolean existsByBranch_ClanIdAndMemberId(String clanId, String memberId);

    List<Member> findByBranch_ClanId(String clanId);

    boolean existsByBranch_ClanIdAndBranch_BranchId(String clanId, String branchId);

    List<Member> findByBranch_ClanIdAndBranch_BranchId(String clanId, String branchId);

    // Duplicate profile detection within one clan: same name (case-insensitive), gender, generation, date of birth.
    // Two variants because "date_of_birth = NULL" never matches in SQL.
    boolean existsByBranch_ClanIdAndFullNameIgnoreCaseAndGenderAndGenerationAndDateOfBirth(
            String clanId, String fullName, String gender, Integer generation, LocalDate dateOfBirth);

    boolean existsByBranch_ClanIdAndFullNameIgnoreCaseAndGenderAndGenerationAndDateOfBirthIsNull(
            String clanId, String fullName, String gender, Integer generation);

    @Query("SELECT m.branch.branchId, COUNT(m) FROM Member m WHERE m.branch.clanId = :clanId GROUP BY m.branch.branchId")
    List<Object[]> countMembersByBranchForClan(@Param("clanId") String clanId);

    @Query(value = """
            SELECT m.* FROM members m
            JOIN family_branches b ON m.branch_id = b.branch_id
            WHERE b.clan_id = :clanId
              AND (CAST(:branchId AS text) IS NULL OR m.branch_id = :branchId)
              AND (:generation IS NULL OR m.generation = :generation)
              AND (CAST(:status AS text) IS NULL OR m.status = :status)
              AND (CAST(:memberId AS text) IS NULL OR m.member_id ILIKE CONCAT('%', CAST(:memberId AS text), '%'))
              AND (CAST(:fullName AS text) IS NULL OR unaccent(CAST(m.full_name AS text)) ILIKE unaccent(CAST(CONCAT('%', CAST(:fullName AS text), '%') AS text)))
              AND (CAST(:keyword AS text) IS NULL OR (
                    m.member_id ILIKE CONCAT('%', CAST(:keyword AS text), '%')
                    OR unaccent(CAST(m.full_name AS text)) ILIKE unaccent(CAST(CONCAT('%', CAST(:keyword AS text), '%') AS text))
                    OR unaccent(CAST(b.branch_name AS text)) ILIKE unaccent(CAST(CONCAT('%', CAST(:keyword AS text), '%') AS text))
                  ))
            ORDER BY m.generation ASC, m.member_id ASC
            """, nativeQuery = true)
    List<Member> searchMembers(
            @Param("clanId") String clanId,
            @Param("keyword") String keyword,
            @Param("memberId") String memberId,
            @Param("fullName") String fullName,
            @Param("generation") Integer generation,
            @Param("branchId") String branchId,
            @Param("status") String status
    );
}
