package com.familyfund.identity.api;

/**
 * Public verification contract exposed by the Identity module to validate account passwords
 * for sensitive financial authorizations (e.g., Clan Head approving common fund disbursements, US-60).
 */
public interface AccountPasswordVerificationService {

    /**
     * Checks whether the specified account exists, is {@code Active}, and matches {@code rawPassword}.
     *
     * @param accountId   account identifier of the caller
     * @param rawPassword plaintext confirmation password submitted by the caller
     * @return {@code true} if active and password matches; {@code false} otherwise
     */
    boolean verifyActiveAccountPassword(String accountId, String rawPassword);
}
