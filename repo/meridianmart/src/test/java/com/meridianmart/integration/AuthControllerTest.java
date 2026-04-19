package com.meridianmart.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerTest extends BaseIntegrationTest {

    @Test
    void loginWithValidUsernameReturns200WithTokenRoleUserId() throws Exception {
        mockMvc.perform(withNoAuthSigning(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("username", "shopper_test", "password", "UserTest123!")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isString())
                .andExpect(jsonPath("$.data.role").value("SHOPPER"))
                .andExpect(jsonPath("$.data.userId").isNumber());
    }

    @Test
    void loginWithEmailIdentifierReturns200() throws Exception {
        mockMvc.perform(withNoAuthSigning(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("username", "user@test.com", "password", "UserTest123!")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isString());
    }

    @Test
    void loginWithWrongPasswordReturns401WithErrorMessage() throws Exception {
        mockMvc.perform(withNoAuthSigning(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("username", "shopper_test", "password", "wrong")))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorMessage").isString());
    }

    @Test
    void loginWithoutSigningHeadersReturns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("username", "shopper_test", "password", "UserTest123!"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fiveWrongAttemptsLeadsTo423OnSixth() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(withNoAuthSigning(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(Map.of("username", "shopper_test", "password", "wrongpass")))));
        }
        mockMvc.perform(withNoAuthSigning(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("username", "shopper_test", "password", "UserTest123!")))))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.errorMessage").isString());
    }

    @Test
    void getMeWithValidJwtReturnsProfileWithoutPassword() throws Exception {
        mockMvc.perform(withShopperAuth(get("/api/auth/me")
                        .contentType(MediaType.APPLICATION_JSON)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("shopper_test"))
                .andExpect(jsonPath("$.data.email").value("user@test.com"))
                .andExpect(jsonPath("$.data.role").value("SHOPPER"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    void getMeWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/auth/me").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutWithValidTokenReturns200() throws Exception {
        mockMvc.perform(withShopperAuth(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void logoutWithoutTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}
