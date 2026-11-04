package com.familyfund.contribution.internal.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Generator for unique electronic receipt numbers (`receipt_number` / `soBienLai`).
 * Implements Task #69: `sinhPhieuThuDienTu()`.
 *
 * <p>Format: {@code PT-yyyyMMdd-XXXXXX} (&le; 30 characters).</p>
 */
@Component
public class ReceiptNumberGenerator {

    public static final String PREFIX = "PT-";
    public static final int MAX_LENGTH = 30;
    private static final int RANDOM_LENGTH = 6;
    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final SecureRandom random = new SecureRandom();

    /**
     * Generates a candidate electronic receipt number.
     * E.g. {@code PT-20261029-A7K9M2} (18 chars &le; 30).
     */
    public String generate() {
        String datePart = LocalDate.now().format(DATE_FORMATTER);
        String suffix = randomSuffix();
        return PREFIX + datePart + "-" + suffix;
    }

    private String randomSuffix() {
        char[] chars = new char[RANDOM_LENGTH];
        for (int i = 0; i < RANDOM_LENGTH; i++) {
            chars[i] = ALPHABET.charAt(random.nextInt(ALPHABET.length()));
        }
        return new String(chars);
    }
}
