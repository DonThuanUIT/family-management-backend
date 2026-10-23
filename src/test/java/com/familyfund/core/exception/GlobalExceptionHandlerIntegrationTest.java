package com.familyfund.core.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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
 * <p>Runs against the full MVC stack. All four cases are rejected before method security runs, so no
 * authentication is needed.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class GlobalExceptionHandlerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Truncated JSON body -> 400 with the standard error envelope")
    void malformedJson_Returns400() throws Exception {
        mockMvc.perform(post("/api/v1/branches/BR_ANY/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Malformed JSON request body"));
    }

    @Test
    @DisplayName("Non-JSON content type -> 415")
    void unsupportedMediaType_Returns415() throws Exception {
        mockMvc.perform(post("/api/v1/branches/BR_ANY/members")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("fullName=An"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Unknown route -> 404 (not 500)")
    void unknownRoute_Returns404() throws Exception {
        mockMvc.perform(get("/api/v1/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Wrong verb on an existing route -> 405 (not 500)")
    void wrongMethod_Returns405() throws Exception {
        mockMvc.perform(put("/api/v1/branches/BR_ANY/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.success").value(false));
    }
}
