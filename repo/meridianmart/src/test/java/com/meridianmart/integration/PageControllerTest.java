package com.meridianmart.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PageControllerTest extends BaseIntegrationTest {

    @Test
    void rootRedirectsToHome() throws Exception {
        mockMvc.perform(withNoAuthSigning(get("/")
                        .contentType(MediaType.TEXT_HTML)))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/home"));
    }

    @Test
    void loginPageIsAccessibleWithoutAuth() throws Exception {
        mockMvc.perform(withNoAuthSigning(get("/login")
                        .contentType(MediaType.TEXT_HTML)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }

    @Test
    void favoritesPageAccessibleWithoutServerAuth() throws Exception {
        mockMvc.perform(get("/favorites")
                        .contentType(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }

    @Test
    void favoritesPageReturns200ForAuthenticatedShopper() throws Exception {
        mockMvc.perform(withShopperAuth(get("/favorites")
                        .contentType(MediaType.TEXT_HTML)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }

    @Test
    void homePageAccessibleWithoutServerAuth() throws Exception {
        mockMvc.perform(get("/home")
                        .contentType(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }

    @Test
    void homePageReturns200ForAuthenticatedShopper() throws Exception {
        mockMvc.perform(withShopperAuth(get("/home")
                        .contentType(MediaType.TEXT_HTML)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }
}
