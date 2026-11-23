package com.familyfund.core.security;

import com.familyfund.core.exception.LedgerImmutabilityViolationException;
import com.familyfund.core.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class LedgerImmutabilityHttpFilterTest {

    private final LedgerImmutabilityHttpFilter filter = new LedgerImmutabilityHttpFilter();
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    @DisplayName("Chặn HTTP PUT, PATCH, DELETE trên mọi endpoint /api/ledger/** và /api/v1/ledger/** bất kể vai trò (ADMIN/CLAN_HEAD)")
    void doFilter_blocksPutPatchDeleteOnAllLedgerRoutes() throws ServletException, IOException {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("ACC_ADMIN", "N/A",
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_CLAN_HEAD")))
        );
        TenantContext.setClanId("CLN_TEST");

        List<String> paths = List.of(
                "/api/ledger",
                "/api/ledger/LDG_001",
                "/api/ledger/adjustments/LAR_001",
                "/api/ledger/disbursements/CFD_001",
                "/api/v1/ledger",
                "/api/v1/ledger/LDG_001",
                "/api/v1/ledger/verify-integrity"
        );
        List<String> blockedMethods = List.of("PUT", "PATCH", "DELETE");

        for (String path : paths) {
            for (String method : blockedMethods) {
                FilterChain chain = mock(FilterChain.class);
                MockHttpServletRequest request = new MockHttpServletRequest(method, path);
                MockHttpServletResponse response = new MockHttpServletResponse();

                filter.doFilter(request, response, chain);

                verify(chain, never()).doFilter(request, response);
                assertThat(response.getStatus()).isEqualTo(405);
                assertThat(response.getHeader("Allow")).isEqualTo("GET, POST, HEAD, OPTIONS");
                JsonNode body = jsonMapper.readTree(response.getContentAsString());
                assertThat(body.get("success").asBoolean()).isFalse();
                assertThat(body.get("code").asText()).isEqualTo(LedgerImmutabilityViolationException.ERROR_CODE);
                assertThat(body.get("message").asText()).contains("NFR-01");
            }
        }
    }

    @Test
    @DisplayName("Chặn cả POST giả lập method qua header X-HTTP-Method-Override: PUT/PATCH/DELETE")
    void doFilter_blocksMethodOverrideHeader() throws ServletException, IOException {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/ledger/LDG_001");
        request.addHeader("X-HTTP-Method-Override", "delete");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(405);
        JsonNode body = jsonMapper.readTree(response.getContentAsString());
        assertThat(body.get("code").asText()).isEqualTo(LedgerImmutabilityViolationException.ERROR_CODE);
    }

    @Test
    @DisplayName("Cho phép GET/POST/OPTIONS trên /api/v1/ledger/** và không chặn PUT/PATCH/DELETE của module khác")
    void doFilter_allowsSafeLedgerVerbsAndOtherModuleRoutes() throws ServletException, IOException {
        for (String allowedVerb : List.of("GET", "POST", "OPTIONS", "HEAD")) {
            FilterChain chain = mock(FilterChain.class);
            MockHttpServletRequest request = new MockHttpServletRequest(allowedVerb, "/api/v1/ledger/disbursements");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);

            verify(chain, times(1)).doFilter(request, response);
        }

        for (String nonLedgerPath : List.of("/api/v1/events/EVT_01", "/api/v1/ledger-other/123")) {
            for (String verb : List.of("PUT", "PATCH", "DELETE")) {
                FilterChain chain = mock(FilterChain.class);
                MockHttpServletRequest request = new MockHttpServletRequest(verb, nonLedgerPath);
                MockHttpServletResponse response = new MockHttpServletResponse();

                filter.doFilter(request, response, chain);

                verify(chain, times(1)).doFilter(request, response);
            }
        }
    }
}
