package com.meridianmart.integration;

import com.meridianmart.model.NonceEntry;
import com.meridianmart.repository.NonceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SecurityTest extends BaseIntegrationTest {

    @Autowired NonceRepository nonceRepository;

    @Test
    void requestMissingXNonceReturns400() throws Exception {
        mockMvc.perform(get("/api/products")
                        .header("Authorization", "Bearer " + shopperToken)
                        .header("X-Timestamp", Instant.now().getEpochSecond())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requestMissingXTimestampReturns400() throws Exception {
        mockMvc.perform(get("/api/products")
                        .header("Authorization", "Bearer " + shopperToken)
                        .header("X-Nonce", "some-nonce-value-123")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requestWithTimestampOlderThan5MinutesIsRejected() throws Exception {
        long oldTimestamp = Instant.now().getEpochSecond() - 400;
        mockMvc.perform(get("/api/products")
                        .header("Authorization", "Bearer " + shopperToken)
                        .header("X-Timestamp", oldTimestamp)
                        .header("X-Nonce", "old-nonce-replay-test")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void apiResponseDoesNotContainRawPasswordField() throws Exception {
        String response = mockMvc.perform(withShopperAuth(get("/api/auth/me")
                        .contentType(MediaType.APPLICATION_JSON)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Verify sensitive fields are absent
        org.assertj.core.api.Assertions.assertThat(response)
                .doesNotContain("passwordHash")
                .doesNotContain("\"password\":");
    }

    @Test
    void requestWithValidSigningHeadersSucceeds() throws Exception {
        mockMvc.perform(withShopperAuth(get("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)))
                .andExpect(status().isOk());
    }

    @Test
    void queryStringIncludedInCanonicalSignatureSucceeds() throws Exception {
        mockMvc.perform(withShopperAuth(get("/api/products?page=0&size=20")
                        .contentType(MediaType.APPLICATION_JSON)))
                .andExpect(status().isOk());
    }

    @Test
    void nonceReuseWithinWindowReturns400() throws Exception {
        String reusedNonce = "nonce-replay-" + UUID.randomUUID();
        NonceEntry entry = new NonceEntry();
        entry.setNonceValue(reusedNonce);
        nonceRepository.save(entry);

        // Signed request using the already-registered nonce must be rejected
        mockMvc.perform(withShopperAuthAndNonce(
                        get("/api/products").contentType(MediaType.APPLICATION_JSON),
                        reusedNonce))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requestWithInvalidSignatureReturns401() throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        mockMvc.perform(get("/api/products")
                        .header("Authorization", "Bearer " + shopperToken)
                        .header("X-Timestamp", timestamp)
                        .header("X-Nonce", "nonce-invalid-sig-" + timestamp)
                        .header("X-Signature", "dGhpc2lzYW5pbnZhbGlkc2lnbmF0dXJl")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}
