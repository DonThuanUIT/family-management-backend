package com.familyfund.notification.internal.config;

import com.familyfund.notification.internal.push.FcmPushNotificationSender;
import com.familyfund.notification.internal.push.GoogleHttpClient;
import com.familyfund.notification.internal.push.PushNotificationSender;
import com.familyfund.notification.internal.push.PushProperties;
import com.familyfund.notification.internal.push.ServiceAccountAccessTokenProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the Firebase Cloud Messaging adapter (US-39).
 *
 * <p>Only created when {@code app.notification.push.provider=fcm}; the default {@code log} adapter is a plain
 * {@code @Component} with the opposite condition, so exactly one {@link PushNotificationSender} bean exists.</p>
 */
@Configuration
@EnableConfigurationProperties(PushProperties.class)
@ConditionalOnProperty(name = "app.notification.push.provider", havingValue = PushProperties.PROVIDER_FCM)
public class FcmPushConfig {

    @Bean
    public GoogleHttpClient googleHttpClient() {
        return new GoogleHttpClient.JdkGoogleHttpClient();
    }

    @Bean
    public ServiceAccountAccessTokenProvider serviceAccountAccessTokenProvider(
            PushProperties pushProperties, GoogleHttpClient googleHttpClient) {
        return new ServiceAccountAccessTokenProvider(pushProperties, googleHttpClient);
    }

    @Bean
    public PushNotificationSender pushNotificationSender(
            PushProperties pushProperties,
            ServiceAccountAccessTokenProvider serviceAccountAccessTokenProvider,
            GoogleHttpClient googleHttpClient) {
        return new FcmPushNotificationSender(pushProperties, serviceAccountAccessTokenProvider, googleHttpClient);
    }
}
