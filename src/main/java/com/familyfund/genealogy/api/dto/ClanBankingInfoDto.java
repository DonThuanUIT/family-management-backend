package com.familyfund.genealogy.api.dto;

/**
 * Public DTO representing the beneficiary banking configuration of a Clan.
 * Used for dynamic multi-tenant VietQR payments and financial reconciliation.
 */
public record ClanBankingInfoDto(
        String clanId,
        String clanName,
        String ancestorName,
        String originLocation,
        Integer establishedYear,
        String description,
        String bankBin,
        String bankName,
        String bankAccountNumber,
        String bankAccountName,
        String qrTemplate
) {
    public ClanBankingInfoDto(
            String clanId,
            String clanName,
            String bankBin,
            String bankName,
            String bankAccountNumber,
            String bankAccountName,
            String qrTemplate
    ) {
        this(clanId, clanName, null, null, null, null, bankBin, bankName, bankAccountNumber, bankAccountName, qrTemplate);
    }
}
