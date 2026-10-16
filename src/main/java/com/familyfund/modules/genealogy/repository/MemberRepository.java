package com.familyfund.modules.genealogy.repository;

import com.familyfund.modules.genealogy.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MemberRepository extends JpaRepository<Member, String> {

    List<Member> findByBranch_BranchId(String branchId);

    boolean existsByBranch_BranchId(String branchId);
}
