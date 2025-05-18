package com.costwise.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final String apiKeyHeader;
    private final String validApiKey;

    public ApiKeyAuthenticationFilter(String apiKeyHeader, String validApiKey) {
        this.apiKeyHeader = apiKeyHeader;
        this.validApiKey = validApiKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String apiKey = request.getHeader(apiKeyHeader);
        
        if (apiKey != null && validateApiKey(apiKey)) {
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "api-user",
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_API"))
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        
        filterChain.doFilter(request, response);
    }

    private boolean validateApiKey(String apiKey) {
        // Compare the provided API key to the configured valid key
        return apiKey != null && apiKey.equals(validApiKey);
    }
} 