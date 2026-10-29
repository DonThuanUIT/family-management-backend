package com.familyfund.contribution.api.dto;

/**
 * Type of contribution made to the clan.
 * Matches the CHECK constraint in table `contributions`.
 */
public enum ContributionType {
    Cash,
    InKind
}
