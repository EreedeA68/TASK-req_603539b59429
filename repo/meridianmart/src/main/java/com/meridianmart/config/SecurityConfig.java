package com.meridianmart.config;

import com.meridianmart.security.JwtAuthenticationFilter;
import com.meridianmart.security.RateLimitingFilter;
import com.meridianmart.security.RequestSigningFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitingFilter rateLimitingFilter;
    private final RequestSigningFilter requestSigningFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/login").permitAll()
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                .requestMatchers("/", "/login", "/error").permitAll()
                .requestMatchers("/css/**", "/js/**", "/images/**").permitAll()
                .requestMatchers("/home", "/products/**").authenticated()
                .requestMatchers("/api/products", "/api/products/**").hasAnyRole("SHOPPER", "STAFF", "ADMIN", "READ_ONLY")
                .requestMatchers("/api/cart/**").hasRole("SHOPPER")
                .requestMatchers("/api/favorites/**").hasRole("SHOPPER")
                .requestMatchers(HttpMethod.POST, "/api/orders").hasRole("SHOPPER")
                .requestMatchers(HttpMethod.POST, "/api/orders/*/pos-confirm").hasAnyRole("STAFF", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/orders/*/ready-for-pickup").hasAnyRole("STAFF", "ADMIN")
                .requestMatchers("/api/orders").hasAnyRole("SHOPPER", "STAFF", "ADMIN")
                .requestMatchers("/api/ratings/**").hasRole("SHOPPER")
                .requestMatchers("/api/notifications/**").hasAnyRole("SHOPPER", "STAFF", "ADMIN")
                .requestMatchers("/api/behavior/**").hasAnyRole("SHOPPER", "STAFF", "ADMIN")
                .requestMatchers("/api/recommendations/**").hasRole("SHOPPER")
                .requestMatchers("/api/payments/**").hasAnyRole("STAFF", "ADMIN")
                .requestMatchers("/api/transactions/**").hasAnyRole("STAFF", "ADMIN")
                .requestMatchers("/api/refunds/**").hasAnyRole("STAFF", "ADMIN")
                .requestMatchers("/api/feature-flags/**").hasRole("ADMIN")
                .requestMatchers("/api/compliance-reports/**").hasRole("ADMIN")
                .requestMatchers("/api/config/**").hasRole("ADMIN")
                .requestMatchers("/api/auth/me").authenticated()
                .requestMatchers("/api/auth/logout").authenticated()
                .requestMatchers("/staff/**").hasAnyRole("STAFF", "ADMIN")
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(rateLimitingFilter, JwtAuthenticationFilter.class)
            .addFilterAfter(requestSigningFilter, RateLimitingFilter.class)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(401);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"success\":false,\"errorMessage\":\"Unauthorized\"}");
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(403);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"success\":false,\"errorMessage\":\"Access denied\"}");
                })
            );

        return http.build();
    }
}
