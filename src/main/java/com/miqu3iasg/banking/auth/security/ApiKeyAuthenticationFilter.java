package com.miqu3iasg.banking.auth.security;

import com.miqu3iasg.banking.auth.domain.ApiKey;
import com.miqu3iasg.banking.auth.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";
    private final ApiKeyService apiKeyService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String rawKey = request.getHeader(API_KEY_HEADER);
        if (rawKey == null || rawKey.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = request.getRemoteAddr();

        try {
            Optional<ApiKey> apiKeyOptional = apiKeyService.validateApiKey(rawKey, clientIp);

            if (apiKeyOptional.isPresent()) {
                ApiKey apiKey = apiKeyOptional.get();

                var authorities = apiKey.getScopes().stream()
                        .map(permission -> new SimpleGrantedAuthority("SCOPE_" + permission.name()))
                        .collect(Collectors.toList());

                ApiKeyAuthenticationToken auth = new ApiKeyAuthenticationToken(apiKey, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);

                log.debug("Authenticated request with API key: {}", apiKey.getKeyPrefix());
            }
        } catch (Exception e) {
            log.warn("API key authentication failed: {}", e.getMessage());
            // Let SecurityErrorHandler handle the exception via the filter chain
            throw e;
        }

        filterChain.doFilter(request, response);
    }
}
