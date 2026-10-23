package com.familyfund.identity.internal.dto.response;

public record ClanResponse(
        String clanId,
        String clanName,
        String ancestorName,
        String originLocation,
        Integer establishedYear,
        String description
) {
}
