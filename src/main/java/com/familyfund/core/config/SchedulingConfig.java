package com.familyfund.core.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's scheduled task execution capability (@Scheduled).
 * Hosts background workers such as the US-08 task deadline scanner.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}