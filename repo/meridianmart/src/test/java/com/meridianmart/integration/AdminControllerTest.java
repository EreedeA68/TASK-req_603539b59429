package com.meridianmart.integration;

import com.meridianmart.model.FeatureFlag;
import com.meridianmart.repository.FeatureFlagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminControllerTest extends BaseIntegrationTest {

    @Autowired FeatureFlagRepository featureFlagRepository;

    private Long flagId;

    @BeforeEach
    void setUpFlags() {
        featureFlagRepository.deleteAll();
        FeatureFlag flag = FeatureFlag.builder()
                .flagName("TEST_FLAG").enabled(true).storeId("STORE_001").updatedBy("admin").build();
        flag = featureFlagRepository.save(flag);
        flagId = flag.getId();
    }

    @Test
    void getFeatureFlagsAsAdminReturns200WithFlagNameIsEnabled() throws Exception {
        mockMvc.perform(withAdminAuth(get("/api/feature-flags")
                        .contentType(MediaType.APPLICATION_JSON)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].flagName").isString())
                .andExpect(jsonPath("$.data[0].enabled").isBoolean());
    }

    @Test
    void updateFeatureFlagAsAdminReturns200WithUpdatedState() throws Exception {
        mockMvc.perform(withAdminAuth(put("/api/feature-flags/" + flagId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("isEnabled", false)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));
    }

    @Test
    void getFeatureFlagsAsShopperReturns403() throws Exception {
        mockMvc.perform(withShopperAuth(get("/api/feature-flags")
                        .contentType(MediaType.APPLICATION_JSON)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getComplianceReportsAsAdminReturns200WithReportData() throws Exception {
        mockMvc.perform(withAdminAuth(get("/api/compliance-reports")
                        .contentType(MediaType.APPLICATION_JSON)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentReconciliation").exists())
                .andExpect(jsonPath("$.data.auditLogCount").isNumber());
    }

    @Test
    void getConfigsAsAdminReturns200WithConfigList() throws Exception {
        mockMvc.perform(withAdminAuth(get("/api/config")
                        .contentType(MediaType.APPLICATION_JSON)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void upsertConfigAsAdminReturns200WithUpdatedValue() throws Exception {
        mockMvc.perform(withAdminAuth(put("/api/config/rate-limit.requests-per-minute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("value", "120")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configKey").value("rate-limit.requests-per-minute"))
                .andExpect(jsonPath("$.data.configValue").value("120"));
    }

    @Test
    void upsertSensitiveConfigKeyReturnsMaskedValue() throws Exception {
        mockMvc.perform(withAdminAuth(put("/api/config/jwt.secret.key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("value", "supersecretvalue123")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configKey").value("jwt.secret.key"))
                // sensitive key: value must be masked (ends with last 4 chars, rest is asterisks)
                .andExpect(jsonPath("$.data.configValue").value("***************e123"));
    }

    @Test
    void getConfigsAsShopperReturns403() throws Exception {
        mockMvc.perform(withShopperAuth(get("/api/config")
                        .contentType(MediaType.APPLICATION_JSON)))
                .andExpect(status().isForbidden());
    }
}
