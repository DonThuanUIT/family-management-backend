package com.familyfund.notification.internal.push;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Firebase Cloud Messaging adapter (FCM HTTP v1), enabled with {@code app.notification.push.provider=fcm} (US-39).
 *
 * <p>One request per device token ({@code POST /v1/projects/{projectId}/messages:send}) because FCM v1 does not
 * accept a token list. A rejected token is reported back so the caller can deactivate it, while any other failure
 * (network, quota, 5xx) is only logged: the notification is already in the recipient's inbox.</p>
 */
@Slf4j
public class FcmPushNotificationSender implements PushNotificationSender {

    private final PushProperties properties;
    private final ServiceAccountAccessTokenProvider tokenProvider;
    private final GoogleHttpClient httpClient;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public FcmPushNotificationSender(PushProperties properties,
                                     ServiceAccountAccessTokenProvider tokenProvider,
                                     GoogleHttpClient httpClient) {
        this.properties = properties;
        this.tokenProvider = tokenProvider;
        this.httpClient = httpClient;
        if (!isEnabled()) {
            log.warn("Push provider is 'fcm' but the configuration is incomplete "
                    + "(project-id / service-account-path); notifications will be persisted without push");
        }
    }

    @Override
    public List<String> send(List<String> deviceTokens, PushMessage message) {
        if (deviceTokens == null || deviceTokens.isEmpty() || !isEnabled()) {
            return List.of();
        }

        String accessToken;
        try {
            accessToken = tokenProvider.accessToken();
        } catch (RuntimeException ex) {
            log.error("FCM access token unavailable, push skipped: {}", ex.getMessage());
            return List.of();
        }

        List<String> invalidTokens = new ArrayList<>();
        for (String deviceToken : deviceTokens) {
            if (deviceToken == null || deviceToken.isBlank()) {
                continue;
            }
            try {
                GoogleHttpClient.HttpResult result = httpClient.postJson(
                        sendUrl(), accessToken, buildPayload(deviceToken, message));
                if (result.isSuccess()) {
                    continue;
                }
                if (isPermanentlyInvalid(result)) {
                    invalidTokens.add(deviceToken);
                    log.warn("FCM rejected device token as unregistered (status {}); it will be deactivated",
                            result.status());
                } else {
                    log.warn("FCM push failed with status {}: {}", result.status(), truncate(result.body()));
                }
            } catch (RuntimeException ex) {
                log.warn("FCM push threw for one device token: {}", ex.getMessage());
            }
        }
        return invalidTokens;
    }

    @Override
    public boolean isEnabled() {
        return properties.isFcm()
                && properties.projectId() != null && !properties.projectId().isBlank()
                && tokenProvider.isConfigured();
    }

    @Override
    public String providerName() {
        return PushProperties.PROVIDER_FCM;
    }

    String sendUrl() {
        return properties.baseUrl() + "/v1/projects/" + properties.projectId() + "/messages:send";
    }

    /** Builds the FCM v1 message body for one device token. */
    String buildPayload(String deviceToken, PushMessage message) {
        ObjectNode root = jsonMapper.createObjectNode();
        ObjectNode payload = root.putObject("message");
        payload.put("token", deviceToken);

        ObjectNode notification = payload.putObject("notification");
        notification.put("title", message.title());
        notification.put("body", message.body());

        if (!message.data().isEmpty()) {
            ObjectNode data = payload.putObject("data");
            message.data().forEach((key, value) -> {
                if (key != null && value != null) {
                    data.put(key, value);
                }
            });
        }

        // High priority + a default sound so the alert shows up immediately on both platforms.
        payload.putObject("android").put("priority", "high");
        payload.putObject("apns").putObject("payload").putObject("aps").put("sound", "default");
        return jsonMapper.writeValueAsString(root);
    }

    private boolean isPermanentlyInvalid(GoogleHttpClient.HttpResult result) {
        if (result.status() == 404) {
            return true;
        }
        String body = result.body() == null ? "" : result.body();
        return body.contains("UNREGISTERED") || (result.status() == 400 && body.contains("INVALID_ARGUMENT"));
    }

    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= 300 ? body : body.substring(0, 300) + "...";
    }
}
