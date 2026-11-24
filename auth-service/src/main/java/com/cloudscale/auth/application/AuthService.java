package com.cloudscale.auth.application;

import com.cloudscale.auth.api.AuthRequests;
import com.cloudscale.auth.api.AuthResponse;
import com.cloudscale.auth.persistence.AuthRepository;
import com.cloudscale.auth.persistence.AuthRepository.AuthAccount;
import com.cloudscale.auth.security.JwtTokenService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {
    private final AuthRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokenService;

    public AuthService(
            AuthRepository repository,
            PasswordEncoder passwordEncoder,
            JwtTokenService tokenService
    ) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    @Transactional
    public AuthResponse register(AuthRequests.RegisterRequest request) {
        validatePassword(request.password());
        String email = normalizeEmail(request.email());
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        String passwordHash = passwordEncoder.encode(request.password());
        AuthAccount account = new AuthAccount(userId, organizationId, request.organizationSlug(),
                request.organizationName().trim(), email, passwordHash, "ADMIN");
        try {
            repository.insertOrganization(organizationId, account.organizationName(), account.organizationSlug(), now);
            repository.insertUser(userId, email, passwordHash, request.fullName().trim(), now);
            repository.insertMembership(UUID.randomUUID(), organizationId, userId, "ADMIN", now);
        } catch (DataIntegrityViolationException exception) {
            throw new AuthConflictException();
        }
        return AuthResponse.from(account, tokenService.issue(account));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(AuthRequests.LoginRequest request) {
        validatePassword(request.password());
        AuthAccount account = repository.findByEmailAndOrganizationSlug(
                        normalizeEmail(request.email()), request.organizationSlug())
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(request.password(), account.passwordHash())) {
            throw new InvalidCredentialsException();
        }
        return AuthResponse.from(account, tokenService.issue(account));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new InvalidAuthRequestException("Password must be at least 12 characters and no more than 72 UTF-8 bytes");
        }
    }
}
