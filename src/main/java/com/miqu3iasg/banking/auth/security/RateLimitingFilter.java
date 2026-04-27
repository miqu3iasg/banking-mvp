package com.miqu3iasg.banking.auth.security;

import com.miqu3iasg.banking.auth.config.AuthProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

	private final RedisRateLimiter redisRateLimiter;
	private final Set<String> trustedProxyIps;

	public RateLimitingFilter (RedisRateLimiter redisRateLimiter, AuthProperties authProperties) {
		this.redisRateLimiter = redisRateLimiter;
		Set<String> configuredProxies = authProperties.getSecurity() != null && authProperties.getSecurity().getTrustedProxyIps() != null
			? Set.copyOf(authProperties.getSecurity().getTrustedProxyIps())
			: Set.of();

		if (configuredProxies.contains("0.0.0.0/0")) {
			throw new IllegalArgumentException("0.0.0.0/0 is not allowed as a trusted proxy — it would trust all client IPs and allow header spoofing");
		}

		this.trustedProxyIps = Set.copyOf(configuredProxies);
	}

	@Override
	protected void doFilterInternal (HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
		throws ServletException, IOException {

		String clientIp = getClientIp(request);

		if (!redisRateLimiter.tryConsumeGeneral(clientIp)) {
			log.warn("General rate limit exceeded from IP: {}", clientIp);
			sendRateLimitResponse(response, "Too many requests. Please try again later.");
			return;
		}

		String path = request.getServletPath();
		if (path.equals("/api/v1/auth/login")) {
			if (!redisRateLimiter.tryConsumeLogin(clientIp)) {
				log.warn("Login rate limit exceeded from IP: {}", clientIp);
				sendRateLimitResponse(response, "Too many login attempts. Please try again later.");
				return;
			}
		} else if (path.equals("/api/v1/auth/password/reset")) {
			if (!redisRateLimiter.tryConsumePasswordReset(clientIp)) {
				log.warn("Password reset rate limit exceeded from IP: {}", clientIp);
				sendRateLimitResponse(response, "Too many password reset requests. Please try again later.");
				return;
			}
		}

		filterChain.doFilter(request, response);
	}

	private String getClientIp (HttpServletRequest request) {
		String remoteAddr = request.getRemoteAddr();
		if (isTrustedProxy(remoteAddr)) {
			String xForwardedFor = request.getHeader("X-Forwarded-For");
			if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
				return xForwardedFor.split(",")[0].trim();
			}
		}
		return remoteAddr;
	}

	private boolean isTrustedProxy (String ip) {
		return trustedProxyIps.contains(ip);
	}

	private void sendRateLimitResponse (HttpServletResponse response, String message) throws IOException {
		response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
		response.setContentType("application/json");
		response.getWriter().write(String.format(
			"{\"code\":\"RATE_001\",\"message\":\"%s\",\"status\":429}", message));
	}

	@Override
	protected boolean shouldNotFilter (HttpServletRequest request) {
		String path = request.getServletPath();
		return path.startsWith("/actuator/") ||
			path.startsWith("/swagger-ui/") ||
			path.startsWith("/v3/api-docs/");
	}

	public void resetForTesting (String ipHash) {
		redisRateLimiter.reset(ipHash);
	}
}
