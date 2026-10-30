package com.familyfund.core.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables Spring's asynchronous method execution capability (@Async).
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
