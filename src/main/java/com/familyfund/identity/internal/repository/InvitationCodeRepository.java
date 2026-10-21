package com.familyfund.identity.internal.repository;

import com.familyfund.identity.internal.entity.InvitationCode;
import com.familyfund.identity.internal.entity.InviteType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvitationCodeRepository extends JpaRepository<InvitationCode, String> {

    /**
     * Checks if a given invite code already exists globally (for unique code generation).
     */
    boolean existsByInviteCode(String inviteCode);

    /**
     * Finds an active invitation by its code (public lookup for onboarding).
     */
    Optional<InvitationCode> findByInviteCodeAndIsActiveTrue(String inviteCode);

    /**
     * Finds an invitation by clan and invite code (multi-tenant scoped lookup).
     */
    Optional<InvitationCode> findByClanIdAndInviteCode(String clanId, String inviteCode);

    /**
     * Lists all active invitations matching a specific type for a clan, ordered by most recent.
     */
    List<InvitationCode> findAllByClanIdAndInviteTypeAndIsActiveTrueOrderByCreatedAtDesc(String clanId, InviteType inviteType);

    /**
     * Lists all invitations created by a specific user account within a clan.
     */
    List<InvitationCode> findAllByClanIdAndCreatedByAccountIdOrderByCreatedAtDesc(String clanId, String createdByAccountId);
}
