package com.familyfund.core.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter that intercepts incoming HTTP requests to resolve the active tenant (Clan ID).
 * Priority: Query parameter (?clanId=...) > Header (X-Clan-Id) > Default (CLAN_DEFAULT).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TenantFilter extends OncePerRequestFilter {

    public static final String HEADER_CLAN_ID = "X-Clan-Id";
    public static final String PARAM_CLAN_ID = "clanId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String clanId = request.getParameter(PARAM_CLAN_ID);
            if (clanId == null || clanId.isBlank()) {
                clanId = request.getHeader(HEADER_CLAN_ID);
            }

            TenantContext.setClanId(clanId);
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
