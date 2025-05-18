package com.costwise.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${security.api-key.header-name}")
    private String apiKeyHeader;

    @Value("${security.api-key.valid-key}")
    private String validApiKey;

    @Value("${security.api-key.enabled}")
    private boolean apiKeyEnabled;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/analyze/**").authenticated()
                .anyRequest().permitAll()
            );

        if (apiKeyEnabled) {
            http.addFilterBefore(apiKeyAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);
        }

        return http.build();
    }

    @Bean
    public ApiKeyAuthenticationFilter apiKeyAuthenticationFilter() {
        return new ApiKeyAuthenticationFilter(apiKeyHeader, validApiKey);
    }
} 