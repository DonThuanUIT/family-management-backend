package com.familyfund.notification.internal.push;

import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Minimal HTTP port used by the Firebase adapter (US-39).
 *
 * <p>Kept as an interface for two reasons: the FCM adapter and the service-account token provider become unit
 * testable without a network, and the JDK {@link HttpClient} stays an implementation detail.</p>
 */
public interface GoogleHttpClient {

    /** @return status code + raw body; never throws for non-2xx responses. */
    HttpResult postJson(String url, String bearerToken, String jsonBody);

    /** @return status code + raw body for a {@code application/x-www-form-urlencoded} POST. */
    HttpResult postForm(String url, String formBody);

    record HttpResult(int status, String body) {
        public boolean isSuccess() {
            return status >= 200 && status < 300;
        }
    }

    /**
     * Default implementation over {@link HttpClient}. Network errors are reported as status {@code 0} so callers
     * can treat them like any other failure instead of catching checked exceptions.
     */
    @Slf4j
    class JdkGoogleHttpClient implements GoogleHttpClient {

        private final HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        @Override
        public HttpResult postJson(String url, String bearerToken, String jsonBody) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
            if (bearerToken != null && !bearerToken.isBlank()) {
                builder.header("Authorization", "Bearer " + bearerToken);
            }
            return execute(builder.build());
        }

        @Override
        public HttpResult postForm(String url, String formBody) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build();
            return execute(request);
        }

        private HttpResult execute(HttpRequest request) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                return new HttpResult(response.statusCode(), response.body());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                log.warn("Push HTTP call interrupted: {}", request.uri(), ex);
                return new HttpResult(0, "interrupted");
            } catch (Exception ex) {
                log.warn("Push HTTP call failed: {} ({})", request.uri(), ex.getMessage());
                return new HttpResult(0, ex.getMessage() == null ? "io-error" : ex.getMessage());
            }
        }
    }
}
