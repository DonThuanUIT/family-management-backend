package com.familyfund.genealogy.internal.repository;

import com.familyfund.genealogy.internal.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MemberRepository extends JpaRepository<Member, String> {

    List<Member> findByBranch_BranchId(String branchId);

    boolean existsByBranch_BranchId(String branchId);

    // Multi-tenant scoped queries
    java.util.Optional<Member> findByBranch_ClanIdAndMemberId(String clanId, String memberId);

    boolean existsByBranch_ClanIdAndMemberId(String clanId, String memberId);

    List<Member> findByBranch_ClanId(String clanId);

    @Query("SELECT m.branch.branchId, COUNT(m) FROM Member m WHERE m.branch.clanId = :clanId GROUP BY m.branch.branchId")
    List<Object[]> countMembersByBranchForClan(@Param("clanId") String clanId);
}
