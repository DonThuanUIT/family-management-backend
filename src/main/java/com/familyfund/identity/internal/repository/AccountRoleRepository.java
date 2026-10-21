package com.familyfund.identity.internal.repository;

import com.familyfund.identity.internal.entity.AccountRole;
import com.familyfund.identity.internal.entity.AccountRoleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA Repository for `AccountRole`.
 */
@Repository
public interface AccountRoleRepository extends JpaRepository<AccountRole, AccountRoleId> {

    List<AccountRole> findByIdAccountId(String accountId);

    boolean existsByIdAccountIdAndIdRoleId(String accountId, String roleId);
}

