package com.familyfund.core.exception;

import com.familyfund.core.security.MemberLinkedPrincipal;
import com.familyfund.core.security.SystemRoles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The catch-all {@code @ExceptionHandler(Exception.class)} must not swallow the 4xx that Spring MVC raises
 * itself, otherwise a malformed body / unknown route / wrong verb is reported as {@code 500} instead of
 * the status the OpenAPI contract documents.
 *
 * <p>Runs against the full MVC stack. The security filter chain rejects unauthenticated requests with
 * {@code 401} before MVC is reached, so every case authenticates as {@code ROLE_CLAN_HEAD}; all four are
 * then rejected before method security runs, which is exactly what this test pins down.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class GlobalExceptionHandlerIntegrationTest {

    private static final String PATH = "/api/v1/branches/BR_ANY/members";

    @Autowired
    private MockMvc mockMvc;

    private record Principal(String getAccountId, String getMemberId) implements MemberLinkedPrincipal {
    }

    /** Authenticates as a clan head so the request reaches MVC instead of the 401 entry point. */
    private Authentication clanHead() {
        return new UsernamePasswordAuthenticationToken(
                new Principal("ACC_EXC_IT", "MEM_EXC_IT"),
                "n/a",
                List.of(new SimpleGrantedAuthority(SystemRoles.CLAN_HEAD)));
    }

    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request) {
        return request.with(authentication(clanHead()));
    }

    @Test
    @DisplayName("Truncated JSON body -> 400 with the standard error envelope")
    void malformedJson_Returns400() throws Exception {
        mockMvc.perform(authenticated(post(PATH))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Malformed JSON request body"));
    }

    @Test
    @DisplayName("Non-JSON content type -> 415")
    void unsupportedMediaType_Returns415() throws Exception {
        mockMvc.perform(authenticated(post(PATH))
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("fullName=An"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Unknown route -> 404 (not 500)")
    void unknownRoute_Returns404() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/does-not-exist")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Wrong verb on an existing route -> 405 (not 500)")
    void wrongMethod_Returns405() throws Exception {
        mockMvc.perform(authenticated(put(PATH))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.success").value(false));
    }
}
