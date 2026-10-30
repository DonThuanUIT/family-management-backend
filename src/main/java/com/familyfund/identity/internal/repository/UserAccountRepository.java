package com.familyfund.identity.internal.repository;

import com.familyfund.identity.internal.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA Repository for `UserAccount`.
 */
@Repository
public interface UserAccountRepository extends JpaRepository<UserAccount, String> {

    boolean existsByPhoneNumber(String phoneNumber);

    boolean existsByEmail(String email);

    Optional<UserAccount> findByPhoneNumber(String phoneNumber);

    Optional<UserAccount> findByEmail(String email);

    Optional<UserAccount> findByMemberId(String memberId);
}
