package com.miqu3iasg.banking.auth.api;

import com.miqu3iasg.banking.auth.api.dto.*;
import com.miqu3iasg.banking.auth.config.AuthProperties;
import com.miqu3iasg.banking.auth.domain.User;
import com.miqu3iasg.banking.auth.service.AuthenticationService;
import com.miqu3iasg.banking.auth.service.AuditLogService;
import com.miqu3iasg.banking.auth.service.HashingService;
import com.miqu3iasg.banking.auth.service.UserService;
import com.miqu3iasg.banking.auth.security.RefreshTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication and authorization endpoints")
public class AuthController {

    private final AuthenticationService authenticationService;
    private final UserService userService;
    private final AuditLogService auditLogService;
    private final HashingService hashingService;
    private final RefreshTokenService refreshTokenService;
    private final AuthProperties authProperties;

    @PostMapping("/register")
    @Operation(summary = "Register a new user account")
    public ResponseEntity<Map<String, Object>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest
    ) {
        User user = userService.createUser(request.getEmail(), request.getPassword(), request.isConsentEmail());

        String ipAddress = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        auditLogService.logRegistration(user.getId(), user.getEmail(), hashingService.ipHash(ipAddress), "SUCCESS");

        log.info("User registered successfully: {}", user.getId());

        return ResponseEntity.ok(Map.of(
            "message", "Registration successful. Please check your email to verify your account.",
            "userId", user.getId()
        ));
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user and obtain tokens")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        String ipAddress = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        AuthenticationService.AuthenticationResult result = authenticationService.authenticate(
            request.getEmail(),
            request.getPassword(),
            ipAddress,
            userAgent,
            request.getDeviceFingerprint()
        );

        AuthResponse response = AuthResponse.builder()
            .accessToken(result.accessToken())
            .refreshToken(result.refreshTokenId())
            .tokenType("Bearer")
            .expiresIn(authProperties.getJwt().getAccessTokenExpirySeconds())
            .roles(result.roles())
            .requiresMfa(result.requiresMfa())
            .build();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token using refresh token")
    public ResponseEntity<AuthResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpRequest
    ) {
        String ipAddress = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        AuthenticationService.AuthenticationResult result = authenticationService.refreshTokens(
            request.getRefreshToken(),
            ipAddress,
            userAgent
        );

        AuthResponse response = AuthResponse.builder()
            .accessToken(result.accessToken())
            .refreshToken(result.refreshTokenId())
            .tokenType("Bearer")
            .expiresIn(authProperties.getJwt().getAccessTokenExpirySeconds())
            .roles(result.roles())
            .requiresMfa(result.requiresMfa())
            .build();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout and invalidate tokens")
    public ResponseEntity<Map<String, String>> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) LogoutRequest logoutRequest,
            HttpServletRequest httpRequest
    ) {
        String accessToken = authorization != null && authorization.startsWith("Bearer ")
            ? authorization.substring(7) : null;
        String refreshTokenValue = logoutRequest != null ? logoutRequest.getRefreshToken() : null;

        authenticationService.logout(accessToken, refreshTokenValue);

        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @PostMapping("/logout/all")
    @Operation(summary = "Logout from all devices")
    public ResponseEntity<Map<String, String>> logoutAll(
            @AuthenticationPrincipal(expression = "userId") String userId
    ) {
        authenticationService.logoutAllDevices(java.util.UUID.fromString(userId));
        return ResponseEntity.ok(Map.of("message", "Logged out from all devices"));
    }

    @PostMapping("/password/reset")
    @Operation(summary = "Request password reset")
    public ResponseEntity<Map<String, String>> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request,
            HttpServletRequest httpRequest
    ) {
        String ipAddress = getClientIp(httpRequest);
        String ipHash = hashingService.ipHash(ipAddress);

        userService.requestPasswordReset(request.getEmail(), ipHash);

        return ResponseEntity.ok(Map.of("message", "If an account with that email exists, a password reset link has been sent"));
    }

    @PostMapping("/password/reset/confirm")
    @Operation(summary = "Confirm password reset with token")
    public ResponseEntity<Map<String, String>> confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmRequest request,
            HttpServletRequest httpRequest
    ) {
        String ipAddress = getClientIp(httpRequest);
        String ipHash = hashingService.ipHash(ipAddress);

        userService.confirmPasswordReset(request.getToken(), request.getNewPassword(), ipHash);

        return ResponseEntity.ok(Map.of("message", "Password reset successful"));
    }

    @PostMapping("/password/change")
    @Operation(summary = "Change password for authenticated user")
    public ResponseEntity<Map<String, String>> changePassword(
            @Valid @RequestBody PasswordChangeRequest request,
            @AuthenticationPrincipal(expression = "userId") String userId,
            HttpServletRequest httpRequest
    ) {
        String ipAddress = getClientIp(httpRequest);
        String ipHash = hashingService.ipHash(ipAddress);

        userService.changePassword(
            java.util.UUID.fromString(userId),
            request.getCurrentPassword(),
            request.getNewPassword()
        );

        auditLogService.logPasswordChange(
            java.util.UUID.fromString(userId),
            ipHash,
            "SUCCESS"
        );

        authenticationService.revokeAllTokensForUser(
            java.util.UUID.fromString(userId),
            "PASSWORD_CHANGE"
        );

        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user information")
    public ResponseEntity<Map<String, Object>> getCurrentUser(
            @AuthenticationPrincipal(expression = "userId") String userId
    ) {
        return userService.findById(java.util.UUID.fromString(userId))
            .map(user -> ResponseEntity.ok(Map.<String, Object>of(
                "id", user.getId(),
                "email", user.getEmail(),
                "status", user.getStatus().name(),
                "mfaEnabled", user.isMfaEnabled()
            )))
            .orElse(ResponseEntity.notFound().build());
    }

    private String getClientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
