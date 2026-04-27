package com.miqu3iasg.banking.auth.security;

import com.miqu3iasg.banking.auth.domain.ApiKey;
import com.miqu3iasg.banking.auth.service.ApiKeyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthenticationFilterTest {

	@Mock
	private ApiKeyService apiKeyService;

	@Mock
	private HttpServletRequest request;

	@Mock
	private HttpServletResponse response;

	@Mock
	private jakarta.servlet.FilterChain filterChain;

	@InjectMocks
	private ApiKeyAuthenticationFilter filter;

	@BeforeEach
	void setUp () {
		SecurityContextHolder.clearContext();
	}

	@Test
	void authenticate_withValidKey_populatesSecurityContext () throws Exception {
		// Arrange
		String rawKey = "bk_valid_key_123";
		String clientIp = "127.0.0.1";
		ApiKey apiKey = ApiKey.builder()
			.keyPrefix("bk_valid")
			.name("Test Key")
			.build();

		when(request.getHeader("X-API-Key")).thenReturn(rawKey);
		when(request.getRemoteAddr()).thenReturn(clientIp);
		when(apiKeyService.validateApiKey(rawKey, clientIp)).thenReturn(Optional.of(apiKey));

		// Act
		filter.doFilterInternal(request, response, filterChain);

		// Assert
		SecurityContext context = SecurityContextHolder.getContext();
		assertThat(context.getAuthentication()).isNotNull();
		assertThat(context.getAuthentication()).isInstanceOf(ApiKeyAuthenticationToken.class);
		assertThat(((ApiKeyAuthenticationToken) context.getAuthentication()).getApiKey()).isEqualTo(apiKey);
	}

	@Test
	void authenticate_withInvalidKey_leavesContextEmpty () throws Exception {
		// Arrange
		String rawKey = "bk_invalid_key";
		String clientIp = "127.0.0.1";
		when(request.getHeader("X-API-Key")).thenReturn(rawKey);
		when(request.getRemoteAddr()).thenReturn(clientIp);
		when(apiKeyService.validateApiKey(rawKey, clientIp)).thenReturn(Optional.empty());

		// Act
		filter.doFilterInternal(request, response, filterChain);

		// Assert
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}

	@Test
	void authenticate_withExpiredOrRevokedKey_throwsException () throws Exception {
		// Arrange
		String rawKey = "bk_expired_key";
		String clientIp = "127.0.0.1";
		when(request.getHeader("X-API-Key")).thenReturn(rawKey);
		when(request.getRemoteAddr()).thenReturn(clientIp);
		when(apiKeyService.validateApiKey(rawKey, clientIp)).thenThrow(new com.miqu3iasg.banking.auth.exception.ApiKeyException(com.miqu3iasg.banking.auth.exception.AuthFaultCode.API_002));

		// Act & Assert
		org.junit.jupiter.api.Assertions.assertThrows(com.miqu3iasg.banking.auth.exception.ApiKeyException.class,
			() -> filter.doFilterInternal(request, response, filterChain));
	}

	@Test
	void authenticate_withIpMismatch_throwsException () throws Exception {
		// Arrange
		String rawKey = "bk_valid_key";
		String clientIp = "192.168.1.1";
		when(request.getHeader("X-API-Key")).thenReturn(rawKey);
		when(request.getRemoteAddr()).thenReturn(clientIp);
		when(apiKeyService.validateApiKey(rawKey, clientIp)).thenThrow(new com.miqu3iasg.banking.auth.exception.ApiKeyException(com.miqu3iasg.banking.auth.exception.AuthFaultCode.API_004));

		// Act & Assert
		org.junit.jupiter.api.Assertions.assertThrows(com.miqu3iasg.banking.auth.exception.ApiKeyException.class,
			() -> filter.doFilterInternal(request, response, filterChain));
	}

	@Test
	void authenticate_withoutKeyHeader_leavesContextEmpty () throws Exception {
		// Arrange
		when(request.getHeader("X-API-Key")).thenReturn(null);

		// Act
		filter.doFilterInternal(request, response, filterChain);

		// Assert
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		verify(filterChain).doFilter(request, response);
	}

}
