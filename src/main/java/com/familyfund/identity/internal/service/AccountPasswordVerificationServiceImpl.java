package com.familyfund.identity.internal.service;

import com.familyfund.identity.api.AccountPasswordVerificationService;
import com.familyfund.identity.internal.entity.AccountStatus;
import com.familyfund.identity.internal.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies user account passwords via BCrypt without exposing identity internal entities.
 */
@Service
@RequiredArgsConstructor
public class AccountPasswordVerificationServiceImpl implements AccountPasswordVerificationService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public boolean verifyActiveAccountPassword(String accountId, String rawPassword) {
        if (accountId == null || accountId.isBlank() || rawPassword == null || rawPassword.isBlank()) {
            return false;
        }
        return userAccountRepository.findById(accountId.trim())
                .filter(account -> account.getAccountStatus() == AccountStatus.Active)
                .map(account -> {
                    String hash = account.getPasswordHash();
                    if (hash == null || hash.isBlank()) {
                        return false;
                    }
                    String trimmed = rawPassword.trim();
                    return passwordEncoder.matches(rawPassword, hash)
                            || (!trimmed.equals(rawPassword) && passwordEncoder.matches(trimmed, hash));
                })
                .orElse(false);
    }
}
