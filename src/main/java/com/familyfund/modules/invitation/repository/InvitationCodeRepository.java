package com.familyfund.modules.invitation.repository;

import com.familyfund.modules.invitation.entity.InvitationCode;
import com.familyfund.modules.invitation.entity.InviteType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvitationCodeRepository extends JpaRepository<InvitationCode, String> {

    /**
     * Checks if a given invite code already exists.
     */
    boolean existsByInviteCode(String inviteCode);

    /**
     * Finds an active invitation by its code.
     */
    Optional<InvitationCode> findByInviteCodeAndIsActiveTrue(String inviteCode);

    /**
     * Lists all active invitations matching a specific type, ordered by most recent.
     */
    List<InvitationCode> findAllByInviteTypeAndIsActiveTrueOrderByCreatedAtDesc(InviteType inviteType);

    /**
     * Lists all invitations created by a specific user account.
     */
    List<InvitationCode> findAllByCreatedByAccountIdOrderByCreatedAtDesc(String createdByAccountId);
}

