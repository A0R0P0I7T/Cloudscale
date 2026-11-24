package com.cloudscale.auth;

import com.cloudscale.auth.api.AuthRequests;
import com.cloudscale.auth.api.AuthResponse;
import com.cloudscale.auth.application.AuthService;
import com.cloudscale.auth.application.AuthConflictException;
import com.cloudscale.auth.application.InvalidCredentialsException;
import com.cloudscale.auth.persistence.AuthRepository;
import com.cloudscale.auth.security.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {
    private final AuthRepository repository = mock(AuthRepository.class);
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtTokenService tokenService = mock(JwtTokenService.class);
    private final AuthService authService = new AuthService(repository, passwordEncoder, tokenService);

    @BeforeEach
    void setUp() {
        when(tokenService.issue(any())).thenReturn(new JwtTokenService.IssuedToken("signed-token", 900));
    }

    @Test
    void registrationHashesPasswordAndCreatesAdministratorMembership() {
        AuthRequests.RegisterRequest request = new AuthRequests.RegisterRequest(
                "CloudScale Demo", "cloudscale-demo", "Ada Lovelace",
                "ADA@EXAMPLE.COM", "a-long-local-password");

        AuthResponse response = authService.register(request);

        assertThat(response.accessToken()).isEqualTo("signed-token");
        org.mockito.ArgumentCaptor<String> hash = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(repository).insertUser(any(UUID.class), org.mockito.ArgumentMatchers.eq("ada@example.com"),
                hash.capture(), org.mockito.ArgumentMatchers.eq("Ada Lovelace"), any(Instant.class));
        assertThat(hash.getValue()).isNotEqualTo(request.password());
        assertThat(passwordEncoder.matches(request.password(), hash.getValue())).isTrue();
        verify(repository).insertMembership(any(UUID.class), any(UUID.class), any(UUID.class),
                org.mockito.ArgumentMatchers.eq("ADMIN"), any(Instant.class));
    }

    @Test
    void duplicateEmailOrSlugIsRejectedAsConflict() {
        doThrow(new DataIntegrityViolationException("unique constraint"))
                .when(repository).insertOrganization(any(UUID.class), anyString(), anyString(), any(Instant.class));

        assertThatThrownBy(() -> authService.register(new AuthRequests.RegisterRequest(
                "CloudScale Demo", "cloudscale-demo", "Ada Lovelace",
                "ada@example.com", "a-long-local-password")))
                .isInstanceOf(AuthConflictException.class);
    }

    @Test
    void loginRequiresAnExistingOrganizationMembershipAndValidPassword() {
        when(repository.findByEmailAndOrganizationSlug("ada@example.com", "cloudscale-demo"))
                .thenReturn(Optional.empty());

        AuthRequests.LoginRequest request = new AuthRequests.LoginRequest(
                "cloudscale-demo", "ada@example.com", "a-long-local-password");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginRejectsIncorrectPassword() {
        AuthRepository.AuthAccount account = new AuthRepository.AuthAccount(
                UUID.randomUUID(), UUID.randomUUID(), "cloudscale-demo", "CloudScale Demo",
                "ada@example.com", passwordEncoder.encode("different-local-password"), "ADMIN");
        when(repository.findByEmailAndOrganizationSlug("ada@example.com", "cloudscale-demo"))
                .thenReturn(Optional.of(account));

        assertThatThrownBy(() -> authService.login(new AuthRequests.LoginRequest(
                "cloudscale-demo", "ada@example.com", "a-long-local-password")))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
