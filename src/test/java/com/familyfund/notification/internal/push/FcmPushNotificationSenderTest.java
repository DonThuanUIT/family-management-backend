package com.familyfund.notification.internal.push;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests of {@link FcmPushNotificationSender} (US-39): FCM HTTP v1 payload, one request per device token, and
 * the handling of permanently invalid tokens. No network is used — {@link GoogleHttpClient} is mocked.
 */
@ExtendWith(MockitoExtension.class)
class FcmPushNotificationSenderTest {

    private static final String PROJECT_ID = "familyfund-test";
    private static final String ACCESS_TOKEN = "ya29.test-token";
    private static final PushMessage MESSAGE = new PushMessage(
            "Bạn được giao việc", "Chuẩn bị mâm cỗ", Map.of("category", "Task", "notificationId", "NTC_1"));

    @Mock
    private GoogleHttpClient httpClient;
    @Mock
    private ServiceAccountAccessTokenProvider tokenProvider;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private FcmPushNotificationSender sender(String provider, String projectId, boolean configured) {
        // lenient: isEnabled() short-circuits before asking the token provider in the disabled cases.
        org.mockito.Mockito.lenient().when(tokenProvider.isConfigured()).thenReturn(configured);
        return new FcmPushNotificationSender(
                new PushProperties(provider, projectId, "/tmp/service-account.json", null, null),
                tokenProvider,
                httpClient);
    }

    // ------------------------------------------------------------------ configuration

    @Test
    @DisplayName("disabled when the configured provider is the logging adapter")
    void disabledForLogProvider() {
        assertThat(sender("log", PROJECT_ID, true).isEnabled()).isFalse();
    }

    @Test
    @DisplayName("disabled when the Firebase project id is missing")
    void disabledWithoutProjectId() {
        assertThat(sender("fcm", "  ", true).isEnabled()).isFalse();
    }

    @Test
    @DisplayName("disabled when the service account is not readable")
    void disabledWithoutServiceAccount() {
        assertThat(sender("fcm", PROJECT_ID, false).isEnabled()).isFalse();
    }

    @Test
    @DisplayName("enabled when provider, project id and service account are all present")
    void enabledWhenFullyConfigured() {
        assertThat(sender("fcm", PROJECT_ID, true).isEnabled()).isTrue();
    }

    @Test
    @DisplayName("send is a no-op while the adapter is disabled")
    void sendSkippedWhenDisabled() {
        List<String> invalid = sender("log", PROJECT_ID, true).send(List.of("TOKEN_A"), MESSAGE);

        assertThat(invalid).isEmpty();
        verify(httpClient, never()).postJson(anyString(), anyString(), anyString());
    }

    // ------------------------------------------------------------------ payload

    @Test
    @DisplayName("posts one FCM v1 message per device token")
    void postsOneMessagePerToken() {
        FcmPushNotificationSender sender = sender("fcm", PROJECT_ID, true);
        when(tokenProvider.accessToken()).thenReturn(ACCESS_TOKEN);
        when(httpClient.postJson(anyString(), anyString(), anyString()))
                .thenReturn(new GoogleHttpClient.HttpResult(200, "{}"));

        List<String> invalid = sender.send(List.of("TOKEN_A", "TOKEN_B"), MESSAGE);

        assertThat(invalid).isEmpty();
        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bearer = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(httpClient, times(2)).postJson(url.capture(), bearer.capture(), body.capture());

        assertThat(url.getAllValues()).containsOnly(
                "https://fcm.googleapis.com/v1/projects/" + PROJECT_ID + "/messages:send");
        assertThat(bearer.getAllValues()).containsOnly(ACCESS_TOKEN);
        assertThat(body.getAllValues().get(0)).contains("TOKEN_A");
        assertThat(body.getAllValues().get(1)).contains("TOKEN_B");
    }

    @Test
    @DisplayName("the message body carries notification, data, android priority and the APNs sound")
    void payloadShape() {
        FcmPushNotificationSender sender = sender("fcm", PROJECT_ID, true);

        JsonNode payload = jsonMapper.readTree(sender.buildPayload("TOKEN_A", MESSAGE));
        JsonNode message = payload.get("message");

        assertThat(message.get("token").asString()).isEqualTo("TOKEN_A");
        assertThat(message.get("notification").get("title").asString()).isEqualTo("Bạn được giao việc");
        assertThat(message.get("notification").get("body").asString()).isEqualTo("Chuẩn bị mâm cỗ");
        assertThat(message.get("data").get("category").asString()).isEqualTo("Task");
        assertThat(message.get("data").get("notificationId").asString()).isEqualTo("NTC_1");
        assertThat(message.get("android").get("priority").asString()).isEqualTo("high");
        assertThat(message.get("apns").get("payload").get("aps").get("sound").asString()).isEqualTo("default");
    }

    @Test
    @DisplayName("a message without data omits the data block")
    void payloadWithoutData() {
        FcmPushNotificationSender sender = sender("fcm", PROJECT_ID, true);

        JsonNode message = jsonMapper.readTree(
                sender.buildPayload("TOKEN_A", new PushMessage("t", "b", Map.of()))).get("message");

        assertThat(message.has("data")).isFalse();
    }

    // ------------------------------------------------------------------ invalid tokens

    @Test
    @DisplayName("a 404 from FCM reports the token as unregistered")
    void unregisteredTokenIsReported() {
        FcmPushNotificationSender sender = sender("fcm", PROJECT_ID, true);
        when(tokenProvider.accessToken()).thenReturn(ACCESS_TOKEN);
        when(httpClient.postJson(anyString(), anyString(), anyString()))
                .thenReturn(new GoogleHttpClient.HttpResult(404,
                        "{\"error\":{\"status\":\"NOT_FOUND\",\"details\":[{\"errorCode\":\"UNREGISTERED\"}]}}"));

        assertThat(sender.send(List.of("TOKEN_DEAD"), MESSAGE)).containsExactly("TOKEN_DEAD");
    }

    @Test
    @DisplayName("a 400 INVALID_ARGUMENT from FCM reports the token as unregistered")
    void invalidArgumentTokenIsReported() {
        FcmPushNotificationSender sender = sender("fcm", PROJECT_ID, true);
        when(tokenProvider.accessToken()).thenReturn(ACCESS_TOKEN);
        when(httpClient.postJson(anyString(), anyString(), anyString()))
                .thenReturn(new GoogleHttpClient.HttpResult(400,
                        "{\"error\":{\"status\":\"INVALID_ARGUMENT\",\"message\":\"token is not valid\"}}"));

        assertThat(sender.send(List.of("TOKEN_BAD"), MESSAGE)).containsExactly("TOKEN_BAD");
    }

    @Test
    @DisplayName("a 500 keeps the token active so the next push retries it")
    void serverErrorDoesNotInvalidateToken() {
        FcmPushNotificationSender sender = sender("fcm", PROJECT_ID, true);
        when(tokenProvider.accessToken()).thenReturn(ACCESS_TOKEN);
        when(httpClient.postJson(anyString(), anyString(), anyString()))
                .thenReturn(new GoogleHttpClient.HttpResult(500, "boom"));

        assertThat(sender.send(List.of("TOKEN_A"), MESSAGE)).isEmpty();
    }

    @Test
    @DisplayName("a network failure (status 0) keeps the token active")
    void networkFailureDoesNotInvalidateToken() {
        FcmPushNotificationSender sender = sender("fcm", PROJECT_ID, true);
        when(tokenProvider.accessToken()).thenReturn(ACCESS_TOKEN);
        when(httpClient.postJson(anyString(), anyString(), anyString()))
                .thenReturn(new GoogleHttpClient.HttpResult(0, "io-error"));

        assertThat(sender.send(List.of("TOKEN_A"), MESSAGE)).isEmpty();
    }

    @Test
    @DisplayName("an unusable access token skips the whole push instead of throwing")
    void tokenProviderFailureSkipsPush() {
        FcmPushNotificationSender sender = sender("fcm", PROJECT_ID, true);
        when(tokenProvider.accessToken()).thenThrow(new IllegalStateException("no credentials"));

        assertThat(sender.send(List.of("TOKEN_A"), MESSAGE)).isEmpty();
        verify(httpClient, never()).postJson(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("blank tokens are ignored and an empty list makes no call")
    void blankTokensAreIgnored() {
        FcmPushNotificationSender sender = sender("fcm", PROJECT_ID, true);
        when(tokenProvider.accessToken()).thenReturn(ACCESS_TOKEN);

        assertThat(sender.send(List.of("  "), MESSAGE)).isEmpty();
        assertThat(sender.send(List.of(), MESSAGE)).isEmpty();
        verify(httpClient, never()).postJson(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("the send URL follows the FCM HTTP v1 path of the configured project")
    void sendUrlUsesProject() {
        FcmPushNotificationSender sender = sender("fcm", PROJECT_ID, true);

        assertThat(sender.sendUrl())
                .isEqualTo("https://fcm.googleapis.com/v1/projects/" + PROJECT_ID + "/messages:send");
        assertThat(sender.providerName()).isEqualTo("fcm");
    }
}
