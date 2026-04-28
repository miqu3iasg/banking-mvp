package com.miqu3iasg.banking.auth.security;

import com.miqu3iasg.banking.auth.domain.AccountStatus;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.UUID;

@Getter
public class AuthenticatedUser implements UserDetails {

    private final String userId;
    private final String email;
    private final Collection<? extends GrantedAuthority> authorities;
    private final AccountStatus status;
    private final boolean accountNonLocked;
    private final boolean enabled;
    private final boolean accountNonExpired;
    private final boolean credentialsNonExpired;

    public AuthenticatedUser(String userId, String email, Collection<? extends GrantedAuthority> authorities,
                             AccountStatus status, boolean accountNonLocked, boolean enabled,
                             boolean accountNonExpired, boolean credentialsNonExpired) {
        this.userId = userId;
        this.email = email;
        this.authorities = authorities;
        this.status = status;
        this.accountNonLocked = accountNonLocked;
        this.enabled = enabled;
        this.accountNonExpired = accountNonExpired;
        this.credentialsNonExpired = credentialsNonExpired;
    }

    public UUID getUserIdAsUUID() {
        return UUID.fromString(userId);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return accountNonExpired;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return credentialsNonExpired;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
