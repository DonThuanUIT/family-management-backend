package com.familyfund.core.security;

import java.security.Principal;

/**
 * Principal produced by {@link JwtAuthenticationFilter} from a validated access token.
 *
 * <p>Implements {@link Principal} so the account id stays the authentication name (and
 * {@code Authentication#getName()} keeps returning {@code user_accounts.account_id}), and
 * {@link MemberLinkedPrincipal} so any module can resolve the genealogy profile bound to the caller
 * ({@code user_accounts.member_id}) without depending on identity internals.</p>
 *
 * @param accountId {@code user_accounts.account_id} (JWT subject)
 * @param memberId  {@code members.member_id}; {@code null} while the account is not linked to a profile
 */
public record JwtPrincipal(String accountId, String memberId) implements Principal, MemberLinkedPrincipal {

    @Override
    public String getName() {
        return accountId;
    }

    @Override
    public String getAccountId() {
        return accountId;
    }

    @Override
    public String getMemberId() {
        return memberId;
    }
}
