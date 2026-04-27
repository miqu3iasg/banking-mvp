package com.miqu3iasg.banking.auth.security;

import com.miqu3iasg.banking.auth.config.AuthProperties;
import com.miqu3iasg.banking.auth.domain.AccountStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final JwtService jwtService;
	private final AuthProperties authProperties;
	private final AntPathMatcher pathMatcher = new AntPathMatcher();
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JwtAuthenticationFilter.class);

	public JwtAuthenticationFilter (JwtService jwtService, AuthProperties authProperties) {
		this.jwtService = jwtService;
		this.authProperties = authProperties;
	}

	@Override
	protected void doFilterInternal (
		@NonNull HttpServletRequest request,
		@NonNull HttpServletResponse response,
		@NonNull FilterChain filterChain
	) throws ServletException, IOException {
		try {
			String jwt = extractJwtFromRequest(request);

			if (StringUtils.hasText(jwt) && jwtService.validateToken(jwt)) {
				String email = jwtService.getEmailFromToken(jwt);
				Set<String> roles = jwtService.getRolesFromToken(jwt);
				AccountStatus status = jwtService.getStatusFromToken(jwt);
				String userId = jwtService.getUserIdFromToken(jwt).toString();

				List<SimpleGrantedAuthority> authorities = roles.stream()
					.map(SimpleGrantedAuthority::new)
					.collect(Collectors.toList());

				boolean accountNonLocked = status != AccountStatus.LOCKED;
				boolean enabled = status != AccountStatus.SUSPENDED && status != AccountStatus.DELETED;
				boolean accountNonExpired = status != AccountStatus.DELETED;
				boolean credentialsNonExpired = true;

				AuthenticatedUser authenticatedUser = new AuthenticatedUser(
					userId, email, authorities, status,
					accountNonLocked, enabled, accountNonExpired, credentialsNonExpired);

				UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
					authenticatedUser,
					null,
					authorities
				);
				authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

				SecurityContextHolder.getContext().setAuthentication(authentication);

				request.setAttribute("userId", userId);
				request.setAttribute("email", email);
			}
		} catch (Exception ex) {
			log.warn("JWT authentication failed: {}", ex.getMessage());
			log.debug("Full authentication failure details", ex);
		}

		filterChain.doFilter(request, response);
	}

	private String extractJwtFromRequest (HttpServletRequest request) {
		String bearerToken = request.getHeader("Authorization");
		if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
			return bearerToken.substring(7);
		}
		return null;
	}

	@Override
	protected boolean shouldNotFilter (HttpServletRequest request) {
		String path = request.getServletPath();
		List<String> publicPaths = authProperties.getSecurity().getPublicPaths();
		return publicPaths.stream()
			.anyMatch(pattern -> pathMatcher.match(pattern, path));
	}
}
