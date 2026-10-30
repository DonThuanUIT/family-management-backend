package com.familyfund.core.security;

import com.familyfund.core.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    @DisplayName("Normal: Request without Authorization header should proceed without setting authentication")
    void doFilter_NoHeader_ProceedsWithoutAuth() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
        verify(jwtTokenProvider, never()).validateToken(anyString());
    }

    @Test
    @DisplayName("Normal: Request with valid Bearer token should set SecurityContext with ROLE_CLAN_HEAD and sync TenantContext")
    void doFilter_ValidToken_SetsAuthenticationAndClanTenancy() throws ServletException, IOException {
        String token = "valid.jwt.token";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getAccountId(token)).thenReturn("ACC_001");
        when(jwtTokenProvider.getMemberId(token)).thenReturn("MEM_001");
        when(jwtTokenProvider.getRoles(token)).thenReturn(Set.of("ROLE_CLAN_HEAD"));
        when(jwtTokenProvider.getClanId(token)).thenReturn("CLAN_TRAN");

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("ACC_001");
        assertThat(auth.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_CLAN_HEAD");

        assertThat(auth.getPrincipal()).isInstanceOf(JwtPrincipal.class);
        JwtPrincipal principal = (JwtPrincipal) auth.getPrincipal();
        assertThat(principal.getAccountId()).isEqualTo("ACC_001");
        assertThat(principal.getMemberId()).isEqualTo("MEM_001");

        // Verify transparent tenancy synchronization (Task #9)
        assertThat(TenantContext.getClanId()).isEqualTo("CLAN_TRAN");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Normal: Request with valid Bearer token having multiple roles should map all GrantedAuthorities")
    void doFilter_ValidTokenWithMultipleRoles_SetsAllAuthorities() throws ServletException, IOException {
        String token = "valid.multirole.token";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getAccountId(token)).thenReturn("ACC_ADMIN");
        when(jwtTokenProvider.getMemberId(token)).thenReturn("MEM_ADMIN");
        when(jwtTokenProvider.getRoles(token)).thenReturn(Set.of("ROLE_CLAN_HEAD", "ROLE_ADMIN", "ROLE_MEMBER"));
        when(jwtTokenProvider.getClanId(token)).thenReturn("CLAN_LE");

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_CLAN_HEAD", "ROLE_ADMIN", "ROLE_MEMBER");
        assertThat(TenantContext.getClanId()).isEqualTo("CLAN_LE");
    }

    @Test
    @DisplayName("Edge case: When TenantContext is 'CLAN_DEFAULT', valid token with clanId should overwrite it")
    void doFilter_ValidToken_OverwritesClanDefaultTenantContext() throws ServletException, IOException {
        TenantContext.setClanId("CLAN_DEFAULT");

        String token = "valid.clan.token";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getAccountId(token)).thenReturn("ACC_002");
        when(jwtTokenProvider.getMemberId(token)).thenReturn("MEM_002");
        when(jwtTokenProvider.getRoles(token)).thenReturn(Set.of("ROLE_CLAN_HEAD"));
        when(jwtTokenProvider.getClanId(token)).thenReturn("CLAN_VU");

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertThat(TenantContext.getClanId()).isEqualTo("CLAN_VU");
    }

    @Test
    @DisplayName("Edge case: When TenantContext already has explicit non-default clan, valid token should not overwrite it")
    void doFilter_ValidToken_DoesNotOverwriteExplicitCustomTenantContext() throws ServletException, IOException {
        TenantContext.setClanId("CLAN_CUSTOM_TENANT");

        String token = "valid.clan.token";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getAccountId(token)).thenReturn("ACC_003");
        when(jwtTokenProvider.getMemberId(token)).thenReturn("MEM_003");
        when(jwtTokenProvider.getRoles(token)).thenReturn(Set.of("ROLE_CLAN_HEAD"));
        when(jwtTokenProvider.getClanId(token)).thenReturn("CLAN_TRAN");

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        // Tenancy was already explicitly established upstream, preserve it
        assertThat(TenantContext.getClanId()).isEqualTo("CLAN_CUSTOM_TENANT");
    }

    @Test
    @DisplayName("Edge case: Valid token with null or empty clanId should not update TenantContext")
    void doFilter_ValidTokenWithBlankClanId_DoesNotOverwriteTenantContext() throws ServletException, IOException {
        String token = "valid.token.noclan";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getAccountId(token)).thenReturn("ACC_GLOBAL_ADMIN");
        when(jwtTokenProvider.getMemberId(token)).thenReturn(null);
        when(jwtTokenProvider.getRoles(token)).thenReturn(Set.of("ROLE_ADMIN"));
        when(jwtTokenProvider.getClanId(token)).thenReturn(null);

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(TenantContext.getClanId()).isEqualTo(TenantContext.DEFAULT_CLAN_ID);
    }

    @Test
    @DisplayName("Edge case: Non-Bearer Authorization header (e.g. Basic auth) should be ignored")
    void doFilter_NonBearerAuthHeader_Ignored() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic dXNlcjpwYXNzd29yZA==");
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtTokenProvider, never()).validateToken(anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Edge case: 'Bearer ' with empty or whitespace-only token should be ignored")
    void doFilter_BearerWithoutToken_Ignored() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer   ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtTokenProvider, never()).validateToken(anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Abnormal: Request with invalid Bearer token should not authenticate")
    void doFilter_InvalidToken_Ignored() throws ServletException, IOException {
        String token = "invalid.jwt.token";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtTokenProvider.validateToken(token)).thenReturn(false);

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Abnormal: When jwtTokenProvider throws an exception, filter should handle gracefully and continue chain")
    void doFilter_TokenProviderThrowsException_HandledGracefully() throws ServletException, IOException {
        String token = "malformed.jwt.token";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtTokenProvider.validateToken(token)).thenThrow(new IllegalArgumentException("JWT claims string is null or empty"));

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        // Security context remains unauthenticated, filter chain continues to next filter
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }
}
