package com.familyfund.core.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Service component responsible for generating, parsing, and validating JWT Tokens (US-10).
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final String secretKeyPlain;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;

    public JwtTokenProvider(
            @Value("${app.jwt.secret:404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}") String secretKeyPlain,
            @Value("${app.jwt.access-token-expiration-ms:3600000}") long accessTokenExpirationMs,
            @Value("${app.jwt.refresh-token-expiration-ms:604800000}") long refreshTokenExpirationMs
    ) {
        this.secretKeyPlain = secretKeyPlain;
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    private SecretKey getSigningKey() {
        try {
            byte[] keyBytes = Decoders.BASE64.decode(secretKeyPlain);
            if (keyBytes.length >= 32) {
                return Keys.hmacShaKeyFor(keyBytes);
            }
        } catch (Exception ignored) {
            // Fallback to UTF-8 bytes if not valid Base64
        }
        byte[] rawBytes = secretKeyPlain.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(rawBytes);
    }

    /**
     * Generates a signed Access Token embedding identity, clan tenancy, and authorization roles.
     */
    public String generateAccessToken(
            String accountId,
            String clanId,
            String memberId,
            String fullName,
            Set<String> roles
    ) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenExpirationMs);

        return Jwts.builder()
                .subject(accountId)
                .claim("clanId", clanId)
                .claim("memberId", memberId)
                .claim("fullName", fullName)
                .claim("roles", roles != null ? roles : Collections.emptySet())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Generates a signed Refresh Token with a longer validity period.
     */
    public String generateRefreshToken(String accountId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshTokenExpirationMs);

        return Jwts.builder()
                .subject(accountId)
                .claim("tokenType", "REFRESH")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Validates signature, structure, and expiration of the provided JWT string.
     */
    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token.trim());
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Parses and returns the payload claims from the token.
     */
    public Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token.trim())
                .getPayload();
    }

    public String getAccountId(String token) {
        return getClaims(token).getSubject();
    }

    public String getClanId(String token) {
        return getClaims(token).get("clanId", String.class);
    }

    public String getMemberId(String token) {
        return getClaims(token).get("memberId", String.class);
    }

    public String getFullName(String token) {
        return getClaims(token).get("fullName", String.class);
    }

    @SuppressWarnings("unchecked")
    public Set<String> getRoles(String token) {
        Object rawRoles = getClaims(token).get("roles");
        if (rawRoles instanceof Collection<?> collection) {
            Set<String> roles = new HashSet<>();
            for (Object obj : collection) {
                if (obj != null) {
                    roles.add(obj.toString());
                }
            }
            return roles;
        }
        return Collections.emptySet();
    }

    public long getAccessTokenExpirationSeconds() {
        return accessTokenExpirationMs / 1000;
    }
}
