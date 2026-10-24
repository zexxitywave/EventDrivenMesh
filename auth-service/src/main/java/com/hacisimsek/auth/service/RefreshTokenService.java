package com.hacisimsek.auth.service;

import com.hacisimsek.auth.model.RefreshToken;
import com.hacisimsek.auth.model.User;
import com.hacisimsek.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${app.jwt.refresh-token-expiration-ms}")
    private long refreshTokenExpirationMs;

    @Transactional
    public RefreshToken createRefreshToken(User user) {
        // Revoke all existing tokens for this user (single active session)
        refreshTokenRepository.revokeAllByUser(user);

        RefreshToken token = RefreshToken.builder()
                .token(UUID.randomUUID().toString())
                .user(user)
                .expiresAt(Instant.now().plusMillis(refreshTokenExpirationMs))
                .revoked(false)
                .build();

        return refreshTokenRepository.save(token);
    }

    @Transactional(readOnly = true)
    public RefreshToken validateRefreshToken(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Refresh token not found"));

        if (refreshToken.isRevoked()) {
            throw new RuntimeException("Refresh token has been revoked");
        }

        if (refreshToken.getExpiresAt().isBefore(Instant.now())) {
            throw new RuntimeException("Refresh token has expired — please login again");
        }

        return refreshToken;
    }

    @Transactional
    public void revokeRefreshToken(String token) {
        refreshTokenRepository.findByToken(token).ifPresent(rt -> {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
        });
    }

    @Transactional
    public void revokeAllTokensForUser(User user) {
        refreshTokenRepository.revokeAllByUser(user);
    }

    /** Runs every night at midnight to clean up expired / revoked tokens. */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void purgeExpiredTokens() {
        refreshTokenRepository.deleteExpiredAndRevoked(Instant.now());
        log.info("Purged expired and revoked refresh tokens");
    }
}
