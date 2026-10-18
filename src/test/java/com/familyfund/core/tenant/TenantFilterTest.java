package com.familyfund.core.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantFilterTest {

    private TenantFilter tenantFilter;

    @BeforeEach
    void setUp() {
        tenantFilter = new TenantFilter();
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("TenantFilter resolves clanId from X-Clan-Id HTTP Header")
    void testFilter_WithHeader() throws ServletException, IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);

        when(request.getHeader(TenantFilter.HEADER_CLAN_ID)).thenReturn("CLAN_NGUYEN");

        AtomicReference<String> capturedTenant = new AtomicReference<>();
        Mockito.doAnswer(invocation -> {
            capturedTenant.set(TenantContext.getClanId());
            return null;
        }).when(filterChain).doFilter(request, response);

        tenantFilter.doFilter(request, response, filterChain);

        assertEquals("CLAN_NGUYEN", capturedTenant.get());
        assertEquals(TenantContext.DEFAULT_CLAN_ID, TenantContext.getClanId(), "Context should be cleared after request");
    }

    @Test
    @DisplayName("TenantFilter resolves clanId from clanId query parameter")
    void testFilter_WithQueryParam() throws ServletException, IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);

        when(request.getParameter(TenantFilter.PARAM_CLAN_ID)).thenReturn("CLAN_LE");

        AtomicReference<String> capturedTenant = new AtomicReference<>();
        Mockito.doAnswer(invocation -> {
            capturedTenant.set(TenantContext.getClanId());
            return null;
        }).when(filterChain).doFilter(request, response);

        tenantFilter.doFilter(request, response, filterChain);

        assertEquals("CLAN_LE", capturedTenant.get());
        assertEquals(TenantContext.DEFAULT_CLAN_ID, TenantContext.getClanId());
    }

    @Test
    @DisplayName("TenantFilter defaults to CLAN_DEFAULT when neither header nor param is present")
    void testFilter_DefaultFallback() throws ServletException, IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);

        AtomicReference<String> capturedTenant = new AtomicReference<>();
        Mockito.doAnswer(invocation -> {
            capturedTenant.set(TenantContext.getClanId());
            return null;
        }).when(filterChain).doFilter(request, response);

        tenantFilter.doFilter(request, response, filterChain);

        assertEquals(TenantContext.DEFAULT_CLAN_ID, capturedTenant.get());
        assertEquals(TenantContext.DEFAULT_CLAN_ID, TenantContext.getClanId());
    }
}
