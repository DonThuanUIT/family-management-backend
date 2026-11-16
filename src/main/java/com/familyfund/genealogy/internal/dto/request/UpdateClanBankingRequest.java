package com.familyfund.genealogy.internal.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request payload for configuring or updating a Clan's beneficiary banking account.
 */
public record UpdateClanBankingRequest(
        @NotBlank(message = "Mã BIN ngân hàng không được để trống")
        @Pattern(regexp = "^[0-9]{6}$", message = "Mã BIN ngân hàng phải gồm đúng 6 chữ số (theo chuẩn Napas)")
        String bankBin,

        @NotBlank(message = "Số tài khoản ngân hàng không được để trống")
        String bankAccountNumber,

        @NotBlank(message = "Tên chủ tài khoản không được để trống")
        String bankAccountName,

        String qrTemplate,
        String clanName,
        String ancestorName,
        String originLocation,
        Integer establishedYear,
        String description
) {
    public UpdateClanBankingRequest(String bankBin, String bankAccountNumber, String bankAccountName, String qrTemplate) {
        this(bankBin, bankAccountNumber, bankAccountName, qrTemplate, null, null, null, null, null);
    }
}
