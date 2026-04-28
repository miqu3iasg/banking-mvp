package com.miqu3iasg.banking.auth.security;

import com.miqu3iasg.banking.auth.domain.ApiKey;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import java.util.Collection;

public class ApiKeyAuthenticationToken extends AbstractAuthenticationToken {
    private final ApiKey apiKey;

    public ApiKeyAuthenticationToken(ApiKey apiKey, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.apiKey = apiKey;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null; // Raw key is not stored for security reasons
    }

    @Override
    public Object getPrincipal() {
        return this.apiKey;
    }

    public ApiKey getApiKey() {
        return apiKey;
    }
}
