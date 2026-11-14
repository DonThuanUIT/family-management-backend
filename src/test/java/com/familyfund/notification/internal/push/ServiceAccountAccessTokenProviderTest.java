package com.familyfund.notification.internal.push;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests of {@link ServiceAccountAccessTokenProvider} (US-39): reading the Google service-account JSON,
 * building the RS256 JWT assertion and caching the exchanged access token. A throwaway RSA key is generated per
 * test, so no real credential is involved.
 */
class ServiceAccountAccessTokenProviderTest {

    private static final String CLIENT_EMAIL = "familyfund-push@familyfund-test.iam.gserviceaccount.com";
    private static final String TOKEN_URI = "https://oauth2.googleapis.com/token";

    @TempDir
    Path tempDir;

    private Path serviceAccountFile;
    private StubHttpClient httpClient;

    /** Records the requests and replays a canned response. */
    private static final class StubHttpClient implements GoogleHttpClient {
        private String lastUrl;
        private String lastBody;
        private int calls;
        private HttpResult response = new HttpResult(200, "{\"access_token\":\"ya29.cached\",\"expires_in\":3600}");

        @Override
        public HttpResult postJson(String url, String bearerToken, String jsonBody) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public HttpResult postForm(String url, String formBody) {
            this.lastUrl = url;
            this.lastBody = formBody;
            this.calls++;
            return response;
        }
    }

    @BeforeEach
    void writeServiceAccount() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
                        .encodeToString(keyPair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";

        ObjectNode credentials = JsonMapper.builder().build().createObjectNode();
        credentials.put("type", "service_account");
        credentials.put("client_email", CLIENT_EMAIL);
        credentials.put("private_key", pem);
        credentials.put("token_uri", TOKEN_URI);

        serviceAccountFile = tempDir.resolve("service-account.json");
        Files.writeString(serviceAccountFile, credentials.toString());

        httpClient = new StubHttpClient();
    }

    private ServiceAccountAccessTokenProvider provider(String path) {
        return new ServiceAccountAccessTokenProvider(
                new PushProperties("fcm", "familyfund-test", path, null, null), httpClient);
    }

    @Test
    @DisplayName("not configured when the service-account path is blank")
    void notConfiguredWithoutPath() {
        assertThat(provider("").isConfigured()).isFalse();
        assertThat(provider(null).isConfigured()).isFalse();
    }

    @Test
    @DisplayName("not configured when the service-account file does not exist")
    void notConfiguredWithMissingFile() {
        assertThat(provider(tempDir.resolve("missing.json").toString()).isConfigured()).isFalse();
    }

    @Test
    @DisplayName("configured when the service-account file is readable")
    void configuredWithFile() {
        assertThat(provider(serviceAccountFile.toString()).isConfigured()).isTrue();
    }

    @Test
    @DisplayName("accessToken throws instead of returning null when the account is missing")
    void accessTokenRequiresConfiguration() {
        assertThatThrownBy(() -> provider("").accessToken())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("service account");
    }

    @Test
    @DisplayName("exchanges the signed assertion for an access token and caches it")
    void exchangesAndCachesToken() {
        ServiceAccountAccessTokenProvider provider = provider(serviceAccountFile.toString());

        String first = provider.accessToken();
        String second = provider.accessToken();

        assertThat(first).isEqualTo("ya29.cached");
        assertThat(second).isEqualTo("ya29.cached");
        assertThat(httpClient.calls).isEqualTo(1);
    }

    @Test
    @DisplayName("posts the jwt-bearer grant to the token endpoint of the service account")
    void postsJwtBearerGrant() {
        provider(serviceAccountFile.toString()).accessToken();

        assertThat(httpClient.lastUrl).isEqualTo(TOKEN_URI);
        assertThat(httpClient.lastBody)
                .startsWith("grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=");
    }

    @Test
    @DisplayName("the assertion is a three-part RS256 JWT carrying iss, scope, aud and exp")
    void assertionClaims() {
        ServiceAccountAccessTokenProvider provider = provider(serviceAccountFile.toString());
        String privateKeyPem = readPrivateKey();

        String assertion = provider.buildAssertion(CLIENT_EMAIL, privateKeyPem, TOKEN_URI,
                java.time.Instant.parse("2026-04-20T09:00:00Z"));

        String[] parts = assertion.split("\\.");
        assertThat(parts).hasSize(3);

        JsonMapper mapper = JsonMapper.builder().build();
        var header = mapper.readTree(Base64.getUrlDecoder().decode(parts[0]));
        assertThat(header.get("alg").asString()).isEqualTo("RS256");
        assertThat(header.get("typ").asString()).isEqualTo("JWT");

        var claims = mapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
        assertThat(claims.get("iss").asString()).isEqualTo(CLIENT_EMAIL);
        assertThat(claims.get("scope").asString())
                .isEqualTo(ServiceAccountAccessTokenProvider.MESSAGING_SCOPE);
        assertThat(claims.get("aud").asString()).isEqualTo(TOKEN_URI);
        assertThat(claims.get("iat").asLong()).isEqualTo(1_776_675_600L);
        assertThat(claims.get("exp").asLong()).isEqualTo(1_776_675_600L + 3600);
        assertThat(Base64.getUrlDecoder().decode(parts[2])).isNotEmpty();
    }

    @Test
    @DisplayName("a rejected token exchange surfaces as an IllegalStateException")
    void rejectedExchangeThrows() {
        httpClient.response = new GoogleHttpClient.HttpResult(401, "{\"error\":\"invalid_grant\"}");
        ServiceAccountAccessTokenProvider provider = provider(serviceAccountFile.toString());

        assertThatThrownBy(provider::accessToken)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("401");
    }

    @Test
    @DisplayName("a service-account file without client_email is rejected")
    void missingClientEmailThrows() throws Exception {
        Files.writeString(serviceAccountFile, "{\"private_key\":\"x\"}");
        ServiceAccountAccessTokenProvider provider = provider(serviceAccountFile.toString());

        assertThatThrownBy(provider::accessToken)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("client_email");
    }

    private String readPrivateKey() {
        try {
            return JsonMapper.builder().build().readTree(Files.readString(serviceAccountFile))
                    .get("private_key").asString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
