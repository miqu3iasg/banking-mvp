package com.miqu3iasg.banking.auth.security;

import com.miqu3iasg.banking.auth.config.AuthProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class RateLimitingFilterTest {

  @Mock
  private RedisRateLimiter redisRateLimiter;

  @Mock
  private AuthProperties authProperties;

  @Mock
  private HttpServletRequest request;

  @Mock
  private HttpServletResponse response;

  @Mock
  private FilterChain filterChain;

  private RateLimitingFilter filter;
  private StringWriter stringWriter;

  @BeforeEach
  void setUp() throws IOException {
    MockitoAnnotations.openMocks(this);
    stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);
    when(response.getWriter()).thenReturn(writer);
    // Default trusted proxy empty list
    AuthProperties.Security security = mock(AuthProperties.Security.class);
    when(security.getTrustedProxyIps()).thenReturn(List.of());
    when(authProperties.getSecurity()).thenReturn(security);
    filter = new RateLimitingFilter(redisRateLimiter, authProperties);
  }

  @Test
  void generalRateLimit_notExceeded_allowsRequest() throws Exception {
    when(redisRateLimiter.tryConsumeGeneral(anyString())).thenReturn(true);
    when(request.getServletPath()).thenReturn("/api/v1/auth/other");
    when(request.getRemoteAddr()).thenReturn("1.2.3.4");

    filter.doFilterInternal(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    verify(response, never()).setStatus(anyInt());
  }

  @Test
  void generalRateLimit_exceeded_returns429() throws Exception {
    when(redisRateLimiter.tryConsumeGeneral(anyString())).thenReturn(false);
    when(request.getRemoteAddr()).thenReturn("5.6.7.8");
    when(request.getServletPath()).thenReturn("/api/v1/auth/any");

    filter.doFilterInternal(request, response, filterChain);

    verify(response).setStatus(429);
    verify(response).setContentType("application/json");
    verify(filterChain, never()).doFilter(request, response);
    // Verify the written JSON
    String expected = "{\"code\":\"RATE_001\",\"message\":\"Too many requests. Please try again later.\",\"status\":429}";
    assertEquals(expected, stringWriter.toString());
  }

  @Test
  void loginRateLimit_exceeded_returns429() throws Exception {
    when(redisRateLimiter.tryConsumeGeneral(anyString())).thenReturn(true);
    when(redisRateLimiter.tryConsumeLogin(anyString())).thenReturn(false);
    when(request.getRemoteAddr()).thenReturn("9.9.9.9");
    when(request.getServletPath()).thenReturn("/api/v1/auth/login");

    filter.doFilterInternal(request, response, filterChain);

    verify(response).setStatus(429);
    verify(response).setContentType("application/json");
    verify(filterChain, never()).doFilter(request, response);
    // Verify the written JSON
    String expected = "{\"code\":\"RATE_001\",\"message\":\"Too many login attempts. Please try again later.\",\"status\":429}";
    assertEquals(expected, stringWriter.toString());
  }

  @Test
  void trustedProxyIp_respectsXForwardedForHeader() throws Exception {
    // Simulate a trusted proxy configuration
    AuthProperties.Security security = mock(AuthProperties.Security.class);
    when(security.getTrustedProxyIps()).thenReturn(List.of("10.0.0.1"));
    when(authProperties.getSecurity()).thenReturn(security);
    filter = new RateLimitingFilter(redisRateLimiter, authProperties);

    when(redisRateLimiter.tryConsumeGeneral(anyString())).thenReturn(true);
    when(request.getRemoteAddr()).thenReturn("10.0.0.1"); // proxy IP
    when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.5, 10.0.0.1");
    when(request.getServletPath()).thenReturn("/api/v1/auth/other");

    filter.doFilterInternal(request, response, filterChain);

    // Verify that the client IP used is the first in X-Forwarded-For
    verify(redisRateLimiter).tryConsumeGeneral("203.0.113.5");
    verify(filterChain).doFilter(request, response);
  }
}
