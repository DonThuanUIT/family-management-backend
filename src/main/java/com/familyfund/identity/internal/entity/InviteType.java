package com.familyfund.identity.internal.entity;

/**
 * Catalog for invite code classification.
 * Matches Postgres CHECK constraint: CHECK (invite_type IN ('GeneralClanInvite', 'PositionLinkCode'))
 */
public enum InviteType {
    GeneralClanInvite,
    PositionLinkCode
}

