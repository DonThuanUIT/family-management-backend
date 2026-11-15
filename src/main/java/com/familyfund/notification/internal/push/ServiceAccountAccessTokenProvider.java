package com.familyfund.notification.internal.push;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;

/**
 * Obtains an OAuth2 access token for Firebase Cloud Messaging from a Google service-account JSON key (US-39).
 *
 * <p>Deliberately dependency-free: the JWT assertion is signed with {@code java.security} (RS256) and the token is
 * exchanged over {@link GoogleHttpClient}, so no Firebase SDK is pulled into the application. The token is cached
 * until shortly before it expires.</p>
 *
 * <p>Flow (Google "OAuth 2.0 for service accounts"):</p>
 * <ol>
 *   <li>build {@code {iss, scope, aud, iat, exp}} and sign it with the account's private key,</li>
 *   <li>POST {@code grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=<jwt>} to the token endpoint,</li>
 *   <li>cache {@code access_token} for {@code expires_in - 60s}.</li>
 * </ol>
 */
@Slf4j
public class ServiceAccountAccessTokenProvider {

    static final String MESSAGING_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
    private static final long ASSERTION_TTL_SECONDS = 3600;
    private static final long EXPIRY_SAFETY_SECONDS = 60;

    private final PushProperties properties;
    private final GoogleHttpClient httpClient;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private volatile String cachedToken;
    private volatile Instant cachedUntil = Instant.EPOCH;

    public ServiceAccountAccessTokenProvider(PushProperties properties, GoogleHttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
    }

    /**
     * @return true when a readable service-account file is configured; false lets the caller degrade to logging.
     */
    public boolean isConfigured() {
        String path = properties.serviceAccountPath();
        if (path == null || path.isBlank()) {
            return false;
        }
        try {
            return Files.isReadable(Path.of(path));
        } catch (RuntimeException ex) {
            // A malformed path must degrade to "not configured", never break application startup.
            log.warn("Invalid Firebase service-account path '{}': {}", path, ex.getMessage());
            return false;
        }
    }

    /**
     * @return a valid bearer token, cached between calls.
     * @throws IllegalStateException when the service account is missing or Google rejects the assertion.
     */
    public String accessToken() {
        Instant now = Instant.now();
        String token = cachedToken;
        if (token != null && now.isBefore(cachedUntil)) {
            return token;
        }
        synchronized (this) {
            if (cachedToken != null && Instant.now().isBefore(cachedUntil)) {
                return cachedToken;
            }
            TokenResponse response = requestToken(Instant.now());
            cachedToken = response.accessToken();
            cachedUntil = Instant.now().plusSeconds(Math.max(response.expiresIn() - EXPIRY_SAFETY_SECONDS, 30));
            return cachedToken;
        }
    }

    private TokenResponse requestToken(Instant now) {
        if (!isConfigured()) {
            throw new IllegalStateException("Firebase service account is not configured (app.notification.push.service-account-path)");
        }
        JsonNode credentials = readCredentials();
        String clientEmail = text(credentials, "client_email");
        String privateKeyPem = text(credentials, "private_key");
        String tokenUri = credentials.has("token_uri") ? text(credentials, "token_uri") : properties.tokenUri();

        String assertion = buildAssertion(clientEmail, privateKeyPem, tokenUri, now);
        String form = "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=" + urlEncode(assertion);
        GoogleHttpClient.HttpResult result = httpClient.postForm(tokenUri, form);

        if (!result.isSuccess()) {
            throw new IllegalStateException("FCM token exchange failed with status " + result.status() + ": " + result.body());
        }

        JsonNode body = jsonMapper.readTree(result.body());
        String accessToken = text(body, "access_token");
        long expiresIn = body.has("expires_in") ? body.get("expires_in").asLong(ASSERTION_TTL_SECONDS) : ASSERTION_TTL_SECONDS;
        log.debug("FCM access token obtained for {} (expires in {}s)", clientEmail, expiresIn);
        return new TokenResponse(accessToken, expiresIn);
    }

    String buildAssertion(String clientEmail, String privateKeyPem, String tokenUri, Instant now) {
        long issuedAt = now.getEpochSecond();
        String header = base64Url("{\"alg\":\"RS256\",\"typ\":\"JWT\"}");
        String claims = base64Url("{"
                + "\"iss\":\"" + clientEmail + "\","
                + "\"scope\":\"" + MESSAGING_SCOPE + "\","
                + "\"aud\":\"" + tokenUri + "\","
                + "\"iat\":" + issuedAt + ","
                + "\"exp\":" + (issuedAt + ASSERTION_TTL_SECONDS)
                + "}");
        String signingInput = header + "." + claims;
        String signature = base64Url(sign(signingInput, privateKeyPem));
        return signingInput + "." + signature;
    }

    private byte[] sign(String signingInput, String privateKeyPem) {
        try {
            String normalized = privateKeyPem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(normalized);
            PrivateKey privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
            return signature.sign();
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot sign the FCM assertion with the configured service account", ex);
        }
    }

    private JsonNode readCredentials() {
        try {
            String content = Files.readString(Path.of(properties.serviceAccountPath()));
            return jsonMapper.readTree(content);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot read the Firebase service account file: " + properties.serviceAccountPath(), ex);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isString() || value.asString().isBlank()) {
            throw new IllegalStateException("Firebase service account is missing field '" + field + "'");
        }
        return value.asString();
    }

    private static String base64Url(String value) {
        return base64Url(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record TokenResponse(String accessToken, long expiresIn) {}
}
