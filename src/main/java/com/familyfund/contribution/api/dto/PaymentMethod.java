package com.familyfund.contribution.api.dto;

/**
 * Payment method for cash contributions.
 * Matches the CHECK constraint in table `cash_contributions`.
 */
public enum PaymentMethod {
    Cash,
    BankTransfer
}
