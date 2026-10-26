package com.omni.identity.service;

import com.omni.common.exception.BusinessException;
import com.omni.identity.domain.RefreshToken;
import com.omni.identity.domain.Role;
import com.omni.identity.domain.User;
import com.omni.identity.repository.RefreshTokenRepository;
import com.omni.identity.repository.UserRepository;
import com.omni.identity.web.Dtos.LoginRequest;
import com.omni.identity.web.Dtos.RegisterRequest;
import com.omni.identity.web.Dtos.TokenResponse;
import com.omni.identity.web.Dtos.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public TokenResponse register(RegisterRequest req) {
        if (users.existsByEmailIgnoreCase(req.email())) {
            throw BusinessException.conflict("EMAIL_EXISTS", "Email is already registered");
        }
        User user = new User();
        user.setEmail(req.email().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setFullName(req.fullName());
        user.setPhone(req.phone());
        user.setRoles(new HashSet<>(Set.of(Role.CUSTOMER)));
        users.save(user);
        return issueTokens(user);
    }

    @Transactional
    public TokenResponse login(LoginRequest req) {
        User user = users.findByEmailIgnoreCase(req.email())
                .filter(u -> passwordEncoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> BusinessException.unauthorized("Invalid email or password"));
        if (user.getStatus() == User.Status.LOCKED) {
            throw new BusinessException("ACCOUNT_LOCKED", "Account is locked", 403);
        }
        return issueTokens(user);
    }

    /**
     * Refresh-token rotation: the presented token is revoked and a new pair is issued.
     * Re-using an already revoked token is treated as theft and revokes every session of the user.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public TokenResponse refresh(String rawToken) {
        RefreshToken token = refreshTokens.findByTokenHash(jwtService.hash(rawToken))
                .orElseThrow(() -> BusinessException.unauthorized("Invalid refresh token"));
        if (token.isRevoked()) {
            refreshTokens.revokeAllForUser(token.getUserId());
            throw BusinessException.unauthorized("Refresh token reuse detected, all sessions revoked");
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw BusinessException.unauthorized("Refresh token expired");
        }
        token.setRevoked(true);
        User user = users.findById(token.getUserId()).orElseThrow(() -> BusinessException.notFound("User"));
        if (user.getStatus() == User.Status.LOCKED) {
            throw new BusinessException("ACCOUNT_LOCKED", "Account is locked", 403);
        }
        return issueTokens(user);
    }

    @Transactional
    public void logout(String rawToken) {
        refreshTokens.findByTokenHash(jwtService.hash(rawToken)).ifPresent(t -> t.setRevoked(true));
    }

    private TokenResponse issueTokens(User user) {
        String raw = jwtService.newRefreshToken();
        RefreshToken token = new RefreshToken();
        token.setUserId(user.getId());
        token.setTokenHash(jwtService.hash(raw));
        token.setExpiresAt(jwtService.refreshTokenExpiry());
        refreshTokens.save(token);
        return new TokenResponse(jwtService.issueAccessToken(user), raw, "Bearer",
                jwtService.accessTokenTtlSeconds(), UserResponse.from(user));
    }
}
