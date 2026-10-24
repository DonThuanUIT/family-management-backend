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
}
