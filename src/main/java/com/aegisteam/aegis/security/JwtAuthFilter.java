package com.aegisteam.aegis.security;

import com.aegisteam.aegis.core.user.CustomUserDetailsService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Runs once per request, before Spring Security's own auth filter. Pulls the
 * Bearer access token off the {@code Authorization} header, validates it,
 * and — per playbook 1.3 — reloads the user from the database (via
 * {@link CustomUserDetailsService}) rather than trusting the JWT claims
 * alone, so a disabled/deleted account is rejected immediately even with a
 * still-valid token.
 *
 * <p>Missing or malformed headers are not an error here: the request simply
 * proceeds unauthenticated, and Spring Security's
 * {@code authorizeHttpRequests} rules decide whether that's allowed
 * (public endpoints like {@code /api/v1/auth/**}) or results in a 401/403
 * further down the chain.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthFilter(JwtService jwtService, CustomUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(PREFIX.length());
        try {
            Claims claims = jwtService.parseClaims(token);
            if (!jwtService.isAccessToken(claims)) {
                // A refresh token presented as a bearer token is not valid for API calls.
                filterChain.doFilter(request, response);
                return;
            }

            String email = jwtService.getSubject(claims);
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);
                var authToken =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        } catch (JwtException | IllegalArgumentException e) {
            // Bad/expired token: leave the context unauthenticated and let
            // authorizeHttpRequests reject it with 401 rather than throwing here.
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
