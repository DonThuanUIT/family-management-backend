package com.familyfund.identity.api;

import com.familyfund.identity.api.dto.AccountSummaryDto;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Public contract exposed by the Identity module for managing account roles
 * and resolving account linkages across bounded contexts (US-03).
 */
public interface IdentityRoleCommandService {

    /**
     * Resolves the user account linked to a specific member ID, if one exists.
     *
     * @param memberId ID of the member
     * @return optional containing the account summary
     */
    Optional<AccountSummaryDto> findAccountByMemberId(String memberId);

    /**
     * Resolves account summaries for a collection of member IDs in bulk.
     *
     * @param memberIds collection of member IDs
     * @return map of memberId to AccountSummaryDto
     */
    Map<String, AccountSummaryDto> findAccountsByMemberIds(Collection<String> memberIds);

    /**
     * Validates that the member exists, belongs to the specified clan,
     * and has an active user account.
     *
     * @param memberId ID of the member
     * @param clanId   ID of the tenant clan
     * @return the active account summary
     * @throws com.familyfund.core.exception.BadRequestException if no active account is found
     */
    AccountSummaryDto requireActiveAccountByMemberId(String memberId, String clanId);

    /**
     * Assigns a system role (e.g. ROLE_BRANCH_HEAD) to the user account.
     *
     * @param accountId ID of the user account
     * @param roleId    system role identifier (e.g. ROLE_BRANCH_HEAD)
     */
    void assignRoleToAccount(String accountId, String roleId);

    /**
     * Revokes a system role from the user account.
     *
     * @param accountId ID of the user account
     * @param roleId    system role identifier (e.g. ROLE_BRANCH_HEAD)
     */
    void revokeRoleFromAccount(String accountId, String roleId);

    /**
     * Checks if the user account currently holds a specific role.
     *
     * @param accountId ID of the user account
     * @param roleId    system role identifier
     * @return true if held, false otherwise
     */
    boolean hasRole(String accountId, String roleId);
}
