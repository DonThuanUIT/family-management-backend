package com.familyfund.contribution.api.dto;

/**
 * Processing status for in-kind contributions (US-10, Task #73).
 */
public enum InKindProcessingStatus {
    Pending,
    Consumed,
    Returned,
    AddedToCommonAssets
}
