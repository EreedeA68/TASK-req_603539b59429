package com.meridianmart.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridianmart.model.User;
import com.meridianmart.repository.UserRepository;
import com.meridianmart.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import org.springframework.mock.web.MockServletContext;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected UserRepository userRepository;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected JwtTokenProvider tokenProvider;

    @Value("${app.request-signing.secret}")
    private String signingSecret;

    protected String adminToken;
    protected String staffToken;
    protected String shopperToken;
    protected Long shopperId;

    /** STORE_002 tokens for cross-store isolation tests */
    protected String staffStore2Token;
    protected String shopperStore2Token;
    protected Long shopperStore2Id;

    @BeforeEach
    void setUpUsers() {
        userRepository.deleteAll();

        User admin = User.builder()
                .username("admin_test").email("admin@test.com")
                .passwordHash(passwordEncoder.encode("AdminTest123!"))
                .role(User.Role.ADMIN).build();
        User staff = User.builder()
                .username("staff_test").email("staff@test.com")
                .passwordHash(passwordEncoder.encode("StaffTest123!"))
                .role(User.Role.STAFF).build();
        User shopper = User.builder()
                .username("shopper_test").email("user@test.com")
                .passwordHash(passwordEncoder.encode("UserTest123!"))
                .role(User.Role.SHOPPER).build();
        User staffStore2 = User.builder()
                .username("staff2_test").email("staff2@test.com")
                .passwordHash(passwordEncoder.encode("Staff2Test123!"))
                .role(User.Role.STAFF).storeId("STORE_002").build();
        User shopperStore2 = User.builder()
                .username("shopper2_test").email("user2@test.com")
                .passwordHash(passwordEncoder.encode("User2Test123!"))
                .role(User.Role.SHOPPER).storeId("STORE_002").build();

        admin = userRepository.save(admin);
        staff = userRepository.save(staff);
        shopper = userRepository.save(shopper);
        staffStore2 = userRepository.save(staffStore2);
        shopperStore2 = userRepository.save(shopperStore2);

        shopperId = shopper.getId();
        shopperStore2Id = shopperStore2.getId();

        adminToken = tokenProvider.generateToken(admin.getId(), admin.getEmail(), "ADMIN");
        staffToken = tokenProvider.generateToken(staff.getId(), staff.getEmail(), "STAFF");
        shopperToken = tokenProvider.generateToken(shopper.getId(), shopper.getEmail(), "SHOPPER");
        staffStore2Token = tokenProvider.generateToken(staffStore2.getId(), staffStore2.getEmail(), "STAFF");
        shopperStore2Token = tokenProvider.generateToken(shopperStore2.getId(), shopperStore2.getEmail(), "SHOPPER");
    }

    protected MockHttpServletRequestBuilder withStaffAuth2(MockHttpServletRequestBuilder builder) {
        return signWithToken(builder, staffStore2Token);
    }

    protected MockHttpServletRequestBuilder withShopperAuth2(MockHttpServletRequestBuilder builder) {
        return signWithToken(builder, shopperStore2Token);
    }

    private MockHttpServletRequestBuilder signWithToken(MockHttpServletRequestBuilder builder, String token) {
        return signRequest(builder, signingSecret).header("Authorization", "Bearer " + token);
    }

    // Signs with the configured server secret (for unauthenticated request signing tests).
    protected MockHttpServletRequestBuilder withSigningHeaders(MockHttpServletRequestBuilder builder) {
        return signRequest(builder, signingSecret);
    }

    // Signs with empty key — matches unauthenticated browser requests (e.g. login).
    protected MockHttpServletRequestBuilder withNoAuthSigning(MockHttpServletRequestBuilder builder) {
        return signRequest(builder, "");
    }

    protected MockHttpServletRequestBuilder withAuth(MockHttpServletRequestBuilder builder, String token) {
        return builder.header("Authorization", "Bearer " + token);
    }

    protected MockHttpServletRequestBuilder withAdminAuth(MockHttpServletRequestBuilder builder) {
        return signWithToken(builder, adminToken);
    }

    protected MockHttpServletRequestBuilder withStaffAuth(MockHttpServletRequestBuilder builder) {
        return signWithToken(builder, staffToken);
    }

    protected MockHttpServletRequestBuilder withShopperAuth(MockHttpServletRequestBuilder builder) {
        return signWithToken(builder, shopperToken);
    }

    private MockHttpServletRequestBuilder signRequest(MockHttpServletRequestBuilder builder, String key) {
        return signRequest(builder, key, UUID.randomUUID().toString());
    }

    private MockHttpServletRequestBuilder signRequest(MockHttpServletRequestBuilder builder, String key, String nonce) {
        var tempReq = builder.buildRequest(new MockServletContext());
        String method = tempReq.getMethod();
        String uri = tempReq.getRequestURI();
        String query = tempReq.getQueryString();
        String fullPath = uri + (query != null && !query.isEmpty() ? "?" + query : "");
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        byte[] bodyBytes = new byte[0];
        try { bodyBytes = tempReq.getInputStream().readAllBytes(); } catch (Exception ignored) {}
        String bodyHash = computeSha256Hex(bodyBytes);
        String canonical = method + "\n" + fullPath + "\n" + timestamp + "\n" + nonce + "\n" + bodyHash;
        String signature = computeHmac(canonical, key);
        return builder
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature);
    }

    protected MockHttpServletRequestBuilder withShopperAuthAndNonce(
            MockHttpServletRequestBuilder builder, String nonce) {
        return signRequest(builder, signingSecret, nonce)
                .header("Authorization", "Bearer " + shopperToken);
    }

    private String computeSha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 computation failed", e);
        }
    }

    private String computeHmac(String canonical, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("HMAC computation failed", e);
        }
    }

    protected String toJson(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }
}
