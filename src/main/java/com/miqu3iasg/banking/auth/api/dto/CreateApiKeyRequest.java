package com.miqu3iasg.banking.auth.api.dto;

import com.miqu3iasg.banking.auth.domain.Permission;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateApiKeyRequest {

    @NotBlank(message = "Name is required")
    private String name;
    private String description;
    private Set<Permission> scopes;
    private Set<String> allowedIps;
    private Instant expiresAt;
}
