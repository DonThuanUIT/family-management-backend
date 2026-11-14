package com.familyfund.notification.internal.push;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration of the push channel (US-39).
 *
 * <pre>
 * app:
 *   notification:
 *     push:
 *       provider: log                     # log (default) | fcm
 *       project-id: familyfund-prod       # required when provider=fcm
 *       service-account-path: /run/secrets/firebase-service-account.json
 *       token-uri: https://oauth2.googleapis.com/token
 *       base-url: https://fcm.googleapis.com
 * </pre>
 *
 * <p>{@code provider=log} keeps the feature usable without Firebase: the notification is still persisted in the
 * inbox, only the push step is written to the application log.</p>
 *
 * @param provider           {@code log} or {@code fcm}
 * @param projectId          Firebase project id (FCM HTTP v1 path segment)
 * @param serviceAccountPath path of the Google service-account JSON key (client_email + private_key)
 * @param tokenUri           OAuth2 token endpoint of the service account
 * @param baseUrl            FCM base URL
 */
@ConfigurationProperties(prefix = "app.notification.push")
public record PushProperties(
        String provider,
        String projectId,
        String serviceAccountPath,
        String tokenUri,
        String baseUrl
) {

    public static final String PROVIDER_LOG = "log";
    public static final String PROVIDER_FCM = "fcm";
    public static final String DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token";
    public static final String DEFAULT_BASE_URL = "https://fcm.googleapis.com";

    public PushProperties {
        provider = (provider == null || provider.isBlank()) ? PROVIDER_LOG : provider.trim().toLowerCase();
        tokenUri = (tokenUri == null || tokenUri.isBlank()) ? DEFAULT_TOKEN_URI : tokenUri.trim();
        baseUrl = (baseUrl == null || baseUrl.isBlank()) ? DEFAULT_BASE_URL : trimTrailingSlash(baseUrl.trim());
    }

    public boolean isFcm() {
        return PROVIDER_FCM.equals(provider);
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
