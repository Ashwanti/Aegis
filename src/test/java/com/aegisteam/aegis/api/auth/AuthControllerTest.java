package com.aegisteam.aegis.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aegisteam.aegis.api.auth.dto.RegisterRequest;
import com.aegisteam.aegis.api.auth.dto.TokenResponse;
import com.aegisteam.aegis.core.Role;
import com.aegisteam.aegis.core.org.Org;
import com.aegisteam.aegis.core.org.OrgRepository;
import com.aegisteam.aegis.core.user.CustomUserDetailsService;
import com.aegisteam.aegis.core.user.User;
import com.aegisteam.aegis.core.user.UserPrincipal;
import com.aegisteam.aegis.core.user.UserRepository;
import com.aegisteam.aegis.security.JwtService;
import com.aegisteam.aegis.security.RefreshTokenService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthControllerTest {

    @Test
    void registerReturnsTokensWhenRequestIsValid() {
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        CustomUserDetailsService userDetailsService = mock(CustomUserDetailsService.class);
        JwtService jwtService = mock(JwtService.class);
        RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
        UserRepository userRepository = mock(UserRepository.class);
        OrgRepository orgRepository = mock(OrgRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

        AuthController controller = new AuthController(
                authenticationManager,
                userDetailsService,
                jwtService,
                refreshTokenService,
                userRepository,
                orgRepository,
                passwordEncoder);

        RegisterRequest request = new RegisterRequest(
                "user@example.com", "secret123", "Test User", "Acme Corp", Role.ADMIN);

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-password");
        when(orgRepository.findByNameIgnoreCase("Acme Corp")).thenReturn(Optional.empty());
        when(orgRepository.save(any(Org.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateAccessToken(any(UserPrincipal.class))).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any(UserPrincipal.class))).thenReturn("refresh-token");
        when(jwtService.getAccessTokenTtlSeconds()).thenReturn(3600L);

        var response = controller.register(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        TokenResponse body = response.getBody();
        assertEquals("access-token", body.accessToken());
        assertEquals("refresh-token", body.refreshToken());

        ArgumentCaptor<Org> savedOrg = ArgumentCaptor.forClass(Org.class);
        verify(orgRepository).save(savedOrg.capture());
        assertEquals("Acme Corp", savedOrg.getValue().getName());

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertEquals(Role.ADMIN, savedUser.getValue().getRole());
    }

    @Test
    void registerReusesExistingOrgWithTheSameName() {
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        CustomUserDetailsService userDetailsService = mock(CustomUserDetailsService.class);
        JwtService jwtService = mock(JwtService.class);
        RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
        UserRepository userRepository = mock(UserRepository.class);
        OrgRepository orgRepository = mock(OrgRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

        AuthController controller = new AuthController(
                authenticationManager,
                userDetailsService,
                jwtService,
                refreshTokenService,
                userRepository,
                orgRepository,
                passwordEncoder);

        UUID existingOrgId = UUID.randomUUID();
        Org existingOrg = mock(Org.class);
        when(existingOrg.getId()).thenReturn(existingOrgId);

        // Different casing on purpose — same tenant, so no second org.
        RegisterRequest request = new RegisterRequest(
                "second@example.com", "secret123", "Second User", "acme corp", Role.AUDITOR);

        when(userRepository.findByEmail("second@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-password");
        when(orgRepository.findByNameIgnoreCase("acme corp")).thenReturn(Optional.of(existingOrg));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateAccessToken(any(UserPrincipal.class))).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any(UserPrincipal.class))).thenReturn("refresh-token");
        when(jwtService.getAccessTokenTtlSeconds()).thenReturn(3600L);

        controller.register(request);

        verify(orgRepository, never()).save(any(Org.class));

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertEquals(existingOrgId, savedUser.getValue().getOrgId());
        assertEquals(Role.AUDITOR, savedUser.getValue().getRole());
    }

    @Test
    void organizationNameIsTrimmedSoPaddingDoesNotCreateASecondOrg() {
        RegisterRequest request = new RegisterRequest(
                "user@example.com", "secret123", "Test User", "  Acme Corp  ", Role.ANALYST);

        assertEquals("Acme Corp", request.organizationName());
    }
}
