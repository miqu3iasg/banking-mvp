package com.miqu3iasg.banking.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miqu3iasg.banking.auth.exception.AuthFaultCode;
import com.miqu3iasg.banking.shared.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException {
        handleError(response, HttpStatus.UNAUTHORIZED, AuthFaultCode.AUTH_001);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, org.springframework.security.access.AccessDeniedException accessDeniedException) throws IOException {
        handleError(response, HttpStatus.FORBIDDEN, AuthFaultCode.RBAC_001);
    }

    private void handleError(HttpServletResponse response, HttpStatus status, AuthFaultCode faultCode) throws IOException {
        ErrorResponse errorResponse = new ErrorResponse(
            faultCode.getCode(),
            faultCode.getDefaultMessage(),
            status.value(),
            java.time.Instant.now(),
            null
        );

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}
