package com.familyfund.genealogy.api.dto;

/**
 * Public DTO representing the beneficiary banking configuration of a Clan.
 * Used for dynamic multi-tenant VietQR payments and financial reconciliation.
 */
public record ClanBankingInfoDto(
        String clanId,
        String clanName,
        String bankBin,
        String bankName,
        String bankAccountNumber,
        String bankAccountName,
        String qrTemplate
) {}
