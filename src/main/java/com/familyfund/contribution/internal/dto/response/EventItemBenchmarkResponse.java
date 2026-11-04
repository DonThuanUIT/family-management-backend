package com.familyfund.contribution.internal.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record EventItemBenchmarkResponse(
        String benchmarkId,
        String eventId,
        String clanId,
        String itemName,
        String unit,
        BigDecimal unitPrice,
        String note,
        Instant createdAt
) {}
