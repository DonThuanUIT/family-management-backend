package com.familyfund.contribution.internal.util;

import java.math.BigDecimal;

/**
 * Utility to convert monetary amounts into Vietnamese words for electronic receipts.
 */
public final class MoneyToWordsConverter {

    private static final String[] DIGITS = {
            "không", "một", "hai", "ba", "bốn", "năm", "sáu", "bảy", "tám", "chín"
    };

    private static final String[] UNITS = {
            "", "nghìn", "triệu", "tỷ", "nghìn tỷ", "triệu tỷ"
    };

    private MoneyToWordsConverter() {}

    /**
     * Converts a BigDecimal amount to Vietnamese words string.
     * E.g. 1500000 -> "Một triệu năm trăm nghìn đồng chẵn"
     */
    public static String toVietnameseWords(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            return "Không đồng";
        }

        long value = amount.longValue();
        if (value < 0) {
            return "Âm " + toVietnameseWords(amount.abs());
        }

        StringBuilder result = new StringBuilder();
        int unitIndex = 0;

        while (value > 0) {
            int group = (int) (value % 1000);
            if (group > 0) {
                String groupText = readThreeDigits(group, value >= 1000);
                String unit = UNITS[unitIndex];
                if (!unit.isEmpty()) {
                    groupText = groupText + " " + unit;
                }
                if (result.length() > 0) {
                    result.insert(0, groupText + " ");
                } else {
                    result.insert(0, groupText);
                }
            }
            value /= 1000;
            unitIndex++;
        }

        String output = result.toString().trim();
        if (output.isEmpty()) {
            return "Không đồng";
        }

        // Capitalize first letter
        output = Character.toUpperCase(output.charAt(0)) + output.substring(1) + " đồng chẵn";
        return output;
    }

    private static String readThreeDigits(int number, boolean hasHigherGroup) {
        int hundreds = number / 100;
        int tens = (number % 100) / 10;
        int ones = number % 10;

        StringBuilder sb = new StringBuilder();

        if (hundreds > 0 || hasHigherGroup) {
            sb.append(DIGITS[hundreds]).append(" trăm");
        }

        if (tens > 1) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(DIGITS[tens]).append(" mươi");
            if (ones == 1) {
                sb.append(" mốt");
            } else if (ones == 5) {
                sb.append(" lăm");
            } else if (ones > 0) {
                sb.append(" ").append(DIGITS[ones]);
            }
        } else if (tens == 1) {
            if (sb.length() > 0) sb.append(" ");
            sb.append("mười");
            if (ones == 5) {
                sb.append(" lăm");
            } else if (ones > 0) {
                sb.append(" ").append(DIGITS[ones]);
            }
        } else { // tens == 0
            if (ones > 0) {
                if (sb.length() > 0) {
                    sb.append(" linh ").append(DIGITS[ones]);
                } else {
                    sb.append(DIGITS[ones]);
                }
            }
        }

        return sb.toString().trim();
    }
}
