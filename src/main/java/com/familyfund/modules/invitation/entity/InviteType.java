package com.familyfund.modules.invitation.entity;

/**
 * Catalog for invite code classification.
 * Matches Postgres CHECK constraint: CHECK (invite_type IN ('GeneralClanInvite', 'PositionLinkCode'))
 */
public enum InviteType {
    GeneralClanInvite,
    PositionLinkCode
}

