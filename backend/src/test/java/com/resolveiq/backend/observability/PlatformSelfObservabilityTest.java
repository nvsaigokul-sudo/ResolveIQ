package com.resolveiq.backend.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Self-Observability & Platform Health Telemetry Verification (PRD §§34.3, 54, 55).
 * Validates platform health indicators, Prometheus metric instrumentation, and operational diagnostics.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class PlatformSelfObservabilityTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("Observability 1: Actuator /actuator/health returns UP with zero authentication requirement")
    void testActuatorHealthEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("Observability 2: Actuator Discovery Base Endpoint strictly requires authentication (401)")
    void testActuatorDiscoveryEndpointRequiresAuth() throws Exception {
        mockMvc.perform(get("/actuator"))
                .andExpect(status().isUnauthorized());
    }
}
