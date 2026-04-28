package com.miqu3iasg.banking.auth.exception;

import com.miqu3iasg.banking.shared.exception.FaultCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuthFaultCode implements FaultCode {
    AUTH_001("AUTH_001", "Authentication is required to access this resource", 401),
    AUTH_002("AUTH_002", "The provided credentials are invalid", 401),
    AUTH_003("AUTH_003", "Your account has been locked due to multiple failed login attempts", 423),
    AUTH_004("AUTH_004", "Your account has been suspended", 403),
    AUTH_005("AUTH_005", "Please verify your email address to continue", 403),
    AUTH_006("AUTH_006", "Your session has expired, please login again", 401),
    AUTH_007("AUTH_007", "The provided token is invalid", 401),
    AUTH_008("AUTH_008", "The provided token has expired", 401),
    AUTH_009("AUTH_009", "The provided token has been revoked", 401),
    AUTH_010("AUTH_010", "Potential token theft detected", 401),

    REG_001("REG_001", "An account with this email already exists", 409),
    REG_002("REG_002", "The provided email address is not valid", 400),
    REG_003("REG_003", "Email verification failed", 400),
    REG_004("REG_004", "The verification token has expired", 400),
    REG_005("REG_005", "The verification token is invalid", 400),

    PWD_001("PWD_001", "The password does not meet the required complexity", 400),
    PWD_002("PWD_002", "This password has been found in a data breach", 400),
    PWD_003("PWD_003", "This password was used recently, please choose a different one", 400),
    PWD_004("PWD_004", "The password reset token has expired", 400),
    PWD_005("PWD_005", "The password reset token is invalid", 400),
    PWD_006("PWD_006", "The current password is incorrect", 400),
    PWD_007("PWD_007", "Your password has expired and must be changed", 403),

    MFA_001("MFA_001", "Multi-factor authentication is required", 403),
    MFA_002("MFA_002", "The provided MFA code is invalid", 400),
    MFA_003("MFA_003", "The provided MFA code has expired", 400),
    MFA_004("MFA_004", "Multi-factor authentication is not enabled for this account", 400),
    MFA_005("MFA_005", "No backup codes remaining", 400),
    MFA_006("MFA_006", "The provided backup code is invalid", 400),

    RBAC_001("RBAC_001", "You do not have permission to access this resource", 403),
    RBAC_002("RBAC_002", "Your account does not have the required privileges", 403),
    RBAC_003("RBAC_003", "You are not authorized to impersonate this user", 403),

    API_001("API_001", "The provided API key is invalid", 401),
    API_002("API_002", "The provided API key has expired", 401),
    API_003("API_003", "The provided API key has been revoked", 401),
    API_004("API_004", "The request IP is not allowed for this API key", 403),
    API_005("API_005", "The API key does not have the required scope", 403),

    RATE_001("RATE_001", "Too many requests, please try again later", 429),
    RATE_002("RATE_002", "Too many login attempts, please try again later", 429),

    SYS_001("SYS_001", "The authentication service is temporarily unavailable", 503),
    SYS_002("SYS_002", "Token storage is temporarily unavailable", 503);

    private final String code;
    private final String defaultMessage;
    private final int httpStatus;
}
