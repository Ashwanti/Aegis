package com.aegisteam.aegis.api.auth;

import com.aegisteam.aegis.api.auth.dto.LoginRequest;
import com.aegisteam.aegis.api.auth.dto.RefreshRequest;
import com.aegisteam.aegis.api.auth.dto.RegisterRequest;
import com.aegisteam.aegis.api.auth.dto.TokenResponse;

import com.aegisteam.aegis.core.exception.InvalidCredentialsException;
import com.aegisteam.aegis.core.exception.TokenRefreshException;

import com.aegisteam.aegis.core.org.Org;
import com.aegisteam.aegis.core.org.OrgRepository;
import com.aegisteam.aegis.core.user.CustomUserDetailsService;
import com.aegisteam.aegis.core.user.User;
import com.aegisteam.aegis.core.user.UserPrincipal;
import com.aegisteam.aegis.core.user.UserRepository;

import com.aegisteam.aegis.security.JwtService;
import com.aegisteam.aegis.security.RefreshTokenService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 1.3 endpoints. Both {@code /login} and {@code /refresh} return the
 * same {@link TokenResponse} shape so the frontend Axios interceptor (Phase
 * 3.1) can treat them identically.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService userDetailsService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;
    private final OrgRepository orgRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(
            AuthenticationManager authenticationManager,
            CustomUserDetailsService userDetailsService,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            UserRepository userRepository,
            OrgRepository orgRepository,
            PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.userRepository = userRepository;
        this.orgRepository = orgRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        } catch (BadCredentialsException e) {
            throw new InvalidCredentialsException("Email or password is incorrect");
        }

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(issueTokenPair(principal));
    }

    /**
     * Registering with an {@code organizationName} that already exists joins
     * that org rather than creating a duplicate; the name is the tenant key.
     */
    @PostMapping("/register")
    @Transactional
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new InvalidCredentialsException("Email already registered");
        }

        Org org = orgRepository
                .findByNameIgnoreCase(request.organizationName())
                .orElseGet(() -> orgRepository.save(new Org(request.organizationName())));

        User user = new User(
                org.getId(),
                request.email(),
                passwordEncoder.encode(request.password()),
                request.fullName(),
                request.role());

        User savedUser = userRepository.save(user);

        UserPrincipal principal = new UserPrincipal(savedUser);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(issueTokenPair(principal));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        Claims claims;
        try {
            claims = jwtService.parseClaims(request.refreshToken());
        } catch (JwtException | IllegalArgumentException e) {
            throw new TokenRefreshException("Refresh token is invalid or expired");
        }

        if (!jwtService.isRefreshToken(claims)) {
            throw new TokenRefreshException("Token is not a refresh token");
        }

        String jti = jwtService.getJti(claims);
        if (refreshTokenService.isRevoked(jti)) {
            throw new TokenRefreshException("Refresh token has already been used or revoked");
        }

        // Rotate: this refresh token is now single-use. Blocklisting it here
        // means a stolen-and-replayed token fails on its second use.
        refreshTokenService.revoke(jti, jwtService.remainingTtl(claims));

        String email = jwtService.getSubject(claims);
        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
        UserPrincipal principal = (UserPrincipal) userDetails;

        return ResponseEntity.ok(issueTokenPair(principal));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        try {
            Claims claims = jwtService.parseClaims(request.refreshToken());
            if (jwtService.isRefreshToken(claims)) {
                refreshTokenService.revoke(jwtService.getJti(claims), jwtService.remainingTtl(claims));
            }
        } catch (JwtException | IllegalArgumentException e) {
            // Already invalid/expired — logout is a no-op in that case, not an error.
        }
        return ResponseEntity.noContent().build();
    }

    private TokenResponse issueTokenPair(UserPrincipal principal) {
        String accessToken = jwtService.generateAccessToken(principal);
        String refreshToken = jwtService.generateRefreshToken(principal);
        return TokenResponse.of(accessToken, refreshToken, jwtService.getAccessTokenTtlSeconds());
    }
}
