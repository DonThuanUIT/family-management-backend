package com.familyfund.core.security;

/**
 * Contract for authenticated principals that are linked to a genealogy profile
 * (user_accounts.member_id -> members.member_id).
 *
 * <p>The authentication layer (identity module, BE-Task 3) must expose its principal through this
 * interface so other modules can resolve "who is calling" without depending on identity internals.</p>
 */
public interface MemberLinkedPrincipal {

    /** user_accounts.account_id */
    String getAccountId();

    /** user_accounts.member_id; may be null when the account is not yet linked to a profile. */
    String getMemberId();
}
