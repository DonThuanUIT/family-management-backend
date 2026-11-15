package com.familyfund.core.utils;

import java.util.Map;

/**
 * Standard Napas / State Bank of Vietnam Bank BIN directory.
 * Maps 6-digit Bank Identification Numbers (BIN) to commercial bank names.
 */
public final class BankBinDirectory {

    private static final Map<String, String> BIN_TO_NAME = Map.ofEntries(
            Map.entry("970436", "Vietcombank"),
            Map.entry("970422", "MBBank"),
            Map.entry("970407", "Techcombank"),
            Map.entry("970415", "VietinBank"),
            Map.entry("970418", "BIDV"),
            Map.entry("970423", "TPBank"),
            Map.entry("970432", "VPBank"),
            Map.entry("970416", "ACB"),
            Map.entry("970405", "Agribank"),
            Map.entry("970441", "VIB"),
            Map.entry("970448", "OCB"),
            Map.entry("970443", "SHB"),
            Map.entry("970428", "NamABank"),
            Map.entry("970454", "VietCapitalBank")
    );

    private BankBinDirectory() {
        // Utility class
    }

    /**
     * Looks up the commercial bank name for a given 6-digit BIN code.
     *
     * @param bin 6-digit bank BIN
     * @return friendly bank name or fallback string
     */
    public static String lookupBankName(String bin) {
        if (bin == null || bin.isBlank()) {
            return "Ngân hàng chưa xác định";
        }
        String cleanBin = bin.trim();
        return BIN_TO_NAME.getOrDefault(cleanBin, "Ngân hàng liên minh Napas (BIN: " + cleanBin + ")");
    }

    /**
     * Validates whether a BIN consists of exactly 6 digits.
     */
    public static boolean isValidBin(String bin) {
        return bin != null && bin.matches("^[0-9]{6}$");
    }
}
