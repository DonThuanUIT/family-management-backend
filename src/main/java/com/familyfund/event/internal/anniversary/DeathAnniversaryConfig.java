package com.familyfund.event.internal.anniversary;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link DeathAnniversaryProperties} (US-40).
 *
 * <p>Separate from the scheduler because the properties are also needed by the API and the calculator, which stay
 * active when the cron pass is switched off.</p>
 */
@Configuration
@EnableConfigurationProperties(DeathAnniversaryProperties.class)
public class DeathAnniversaryConfig {
}
