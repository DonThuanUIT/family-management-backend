package com.familyfund.core.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private static final String TEST_SECRET =
            "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final long ACCESS_EXP_MS = 3600000; // 1h
    private static final long REFRESH_EXP_MS = 604800000; // 7d

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(TEST_SECRET, ACCESS_EXP_MS, REFRESH_EXP_MS);
    }

    @Test
    @DisplayName("generateAccessToken should produce valid token embedding custom claims")
    void generateAccessToken_Success() {
        String token = jwtTokenProvider.generateAccessToken(
                "ACC_001",
                "CLAN_NGUYEN",
                "MEM_001",
                "Nguyễn Văn An",
                Set.of("ROLE_CLAN_HEAD")
        );

        assertThat(token).isNotBlank();
        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        assertThat(jwtTokenProvider.getAccountId(token)).isEqualTo("ACC_001");
        assertThat(jwtTokenProvider.getClanId(token)).isEqualTo("CLAN_NGUYEN");
        assertThat(jwtTokenProvider.getMemberId(token)).isEqualTo("MEM_001");
        assertThat(jwtTokenProvider.getFullName(token)).isEqualTo("Nguyễn Văn An");
        assertThat(jwtTokenProvider.getRoles(token)).containsExactly("ROLE_CLAN_HEAD");
        assertThat(jwtTokenProvider.getAccessTokenExpirationSeconds()).isEqualTo(3600);
    }

    @Test
    @DisplayName("generateRefreshToken should produce valid token with accountId subject")
    void generateRefreshToken_Success() {
        String refreshToken = jwtTokenProvider.generateRefreshToken("ACC_001");

        assertThat(refreshToken).isNotBlank();
        assertThat(jwtTokenProvider.validateToken(refreshToken)).isTrue();
        assertThat(jwtTokenProvider.getAccountId(refreshToken)).isEqualTo("ACC_001");
    }

    @Test
    @DisplayName("validateToken should return false for invalid, malformed, or tampered tokens")
    void validateToken_InvalidTokens() {
        assertThat(jwtTokenProvider.validateToken(null)).isFalse();
        assertThat(jwtTokenProvider.validateToken("")).isFalse();
        assertThat(jwtTokenProvider.validateToken("invalid.token.structure")).isFalse();

        String validToken = jwtTokenProvider.generateAccessToken(
                "ACC_001", "CLAN_01", "MEM_01", "Test", Set.of("ROLE_MEMBER")
        );
        String tamperedToken = validToken.substring(0, validToken.length() - 5) + "abcde";
        assertThat(jwtTokenProvider.validateToken(tamperedToken)).isFalse();
    }
}
