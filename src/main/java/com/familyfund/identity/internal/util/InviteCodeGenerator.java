package com.familyfund.identity.internal.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Cryptographically secure generator for invite codes and shareable URLs.
 * Uses Crockford's Base32 alphabet to avoid ambiguous characters (0/O, 1/I/L).
 */
@Component
public class InviteCodeGenerator {

    public static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    public static final int CODE_LENGTH = 8;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final String baseUrl;

    public InviteCodeGenerator(@Value("${app.invitation.base-url:http://localhost:3000/join}") String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            this.baseUrl = "http://localhost:3000/join";
        } else {
            this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        }
    }

    /**
     * Generates an 8-character random invite code.
     */
    public String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            int index = RANDOM.nextInt(ALPHABET.length());
            sb.append(ALPHABET.charAt(index));
        }
        return sb.toString();
    }

    /**
     * Builds the full shareable registration URL for the given invite code.
     */
    public String buildShareableUrl(String inviteCode) {
        if (inviteCode == null || inviteCode.isBlank()) {
            throw new IllegalArgumentException("Mã mời không được để trống khi tạo link chia sẻ");
        }
        return this.baseUrl + "?code=" + inviteCode.trim();
    }

    public String getBaseUrl() {
        return this.baseUrl;
    }
}

