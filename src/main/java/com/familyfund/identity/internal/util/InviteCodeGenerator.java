package com.familyfund.identity.internal.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Cryptographically secure generator for invite codes and shareable URLs.
 * Uses Crockford's Base32 alphabet to avoid ambiguous characters (0/O, 1/I/L).
 *
 * <p>Two redemption links are built from one code generator:</p>
 * <ul>
 *   <li>{@code app.invitation.base-url} - general clan invite (US-16): a code many people may use;</li>
 *   <li>{@code app.invitation.position-link-base-url} - position link code (US-18): a one-time code that claims
 *       exactly one pre-created virtual profile.</li>
 * </ul>
 */
@Component
public class InviteCodeGenerator {

    public static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    public static final int CODE_LENGTH = 8;

    public static final String DEFAULT_BASE_URL = "http://localhost:3000/join";
    public static final String DEFAULT_POSITION_LINK_BASE_URL = "http://localhost:3000/position-link";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final String baseUrl;
    private final String positionLinkBaseUrl;

    public InviteCodeGenerator(
            @Value("${app.invitation.base-url:" + DEFAULT_BASE_URL + "}") String baseUrl,
            @Value("${app.invitation.position-link-base-url:" + DEFAULT_POSITION_LINK_BASE_URL + "}") String positionLinkBaseUrl) {
        this.baseUrl = normalize(baseUrl, DEFAULT_BASE_URL);
        this.positionLinkBaseUrl = normalize(positionLinkBaseUrl, DEFAULT_POSITION_LINK_BASE_URL);
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
        return buildUrl(this.baseUrl, inviteCode, "Mã mời không được để trống khi tạo link chia sẻ");
    }

    /**
     * Builds the redemption URL of a position link code (US-18).
     */
    public String buildPositionLinkUrl(String inviteCode) {
        return buildUrl(this.positionLinkBaseUrl, inviteCode,
                "Mã liên kết vị trí không được để trống khi tạo link chia sẻ");
    }

    public String getBaseUrl() {
        return this.baseUrl;
    }

    public String getPositionLinkBaseUrl() {
        return this.positionLinkBaseUrl;
    }

    private static String buildUrl(String base, String inviteCode, String blankMessage) {
        if (inviteCode == null || inviteCode.isBlank()) {
            throw new IllegalArgumentException(blankMessage);
        }
        return base + "?code=" + inviteCode.trim();
    }

    private static String normalize(String configured, String fallback) {
        if (configured == null || configured.isBlank()) {
            return fallback;
        }
        String trimmed = configured.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
