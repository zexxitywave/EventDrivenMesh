package com.hacisimsek.auth.service;

import com.hacisimsek.auth.dto.*;
import com.hacisimsek.auth.model.AuthProvider;
import com.hacisimsek.auth.model.Role;
import com.hacisimsek.auth.model.User;
import com.hacisimsek.auth.repository.UserRepository;
import com.hacisimsek.auth.security.JwtTokenProvider;
import com.hacisimsek.auth.service.TokenBlacklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final EmailService emailService;
    private final AuthenticationManager authenticationManager;
    private final TokenBlacklistService tokenBlacklistService;

    @Value("${app.email.otp-expiry-minutes}")
    private int otpExpiryMinutes;

    // ── Register ─────────────────────────────────────────────────────────────

    @Transactional
    public ApiResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered");
        }

        String otp = generateOtp();

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .provider(AuthProvider.LOCAL)
                .role(Role.ROLE_USER)
                .emailVerified(false)
                .otp(otp)
                .otpExpiresAt(Instant.now().plus(otpExpiryMinutes, ChronoUnit.MINUTES))
                .build();

        userRepository.save(user);

        // Send OTP asynchronously
        emailService.sendVerificationOtp(user.getEmail(), user.getName(), otp);

        log.info("User registered: {}", user.getEmail());
        return ApiResponse.ok("Registration successful. Please check your email for the OTP to verify your account.");
    }

    // ── Verify Email ─────────────────────────────────────────────────────────

    @Transactional
    public ApiResponse verifyEmail(VerifyEmailRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.isEmailVerified()) {
            return ApiResponse.ok("Email already verified");
        }

        validateOtp(user, request.getOtp());

        user.setEmailVerified(true);
        user.setOtp(null);
        user.setOtpExpiresAt(null);
        userRepository.save(user);

        log.info("Email verified for: {}", user.getEmail());
        return ApiResponse.ok("Email verified successfully. You can now log in.");
    }

    // ── Resend Verification OTP ───────────────────────────────────────────────

    @Transactional
    public ApiResponse resendVerificationOtp(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.isEmailVerified()) {
            return ApiResponse.ok("Email already verified");
        }

        String otp = generateOtp();
        user.setOtp(otp);
        user.setOtpExpiresAt(Instant.now().plus(otpExpiryMinutes, ChronoUnit.MINUTES));
        userRepository.save(user);

        emailService.sendVerificationOtp(user.getEmail(), user.getName(), otp);
        return ApiResponse.ok("OTP resent. Please check your email.");
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Transactional
    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );
        } catch (DisabledException e) {
            throw new RuntimeException("Account not verified. Please verify your email first.");
        } catch (AuthenticationException e) {
            throw new RuntimeException("Invalid email or password");
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        return buildAuthResponse(user);
    }

    // ── Refresh Token ─────────────────────────────────────────────────────────

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        var refreshToken = refreshTokenService.validateRefreshToken(request.getRefreshToken());
        User user = refreshToken.getUser();

        // Rotate: revoke old, issue new
        refreshTokenService.revokeRefreshToken(request.getRefreshToken());

        return buildAuthResponse(user);
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    @Transactional
    public ApiResponse logout(String refreshToken, String accessToken) {
        // 1. Revoke the refresh token (already implemented)
        refreshTokenService.revokeRefreshToken(refreshToken);

        // 2. Blacklist the access token in Redis so it can't be reused
        //    before its natural 15-min expiry
        if (accessToken != null && !accessToken.isBlank()) {
            try {
                String jti = jwtTokenProvider.getJtiFromToken(accessToken);
                java.util.Date expiry = jwtTokenProvider.getExpiryFromToken(accessToken);
                tokenBlacklistService.blacklist(jti, expiry);
                log.info("Access token blacklisted on logout (jti={})", jti);
            } catch (Exception e) {
                // Don't fail logout if token is already expired or malformed
                log.warn("Could not blacklist access token on logout: {}", e.getMessage());
            }
        }
        return ApiResponse.ok("Logged out successfully");
    }

    // ── Forgot Password ───────────────────────────────────────────────────────

    @Transactional
    public ApiResponse forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmail(request.getEmail()).ifPresent(user -> {
            if (user.getProvider() != AuthProvider.LOCAL) {
                return; // OAuth2 users don't have passwords
            }
            String otp = generateOtp();
            user.setOtp(otp);
            user.setOtpExpiresAt(Instant.now().plus(otpExpiryMinutes, ChronoUnit.MINUTES));
            userRepository.save(user);
            emailService.sendPasswordResetOtp(user.getEmail(), user.getName(), otp);
        });

        // Always return success to prevent email enumeration
        return ApiResponse.ok("If this email is registered, a password reset OTP has been sent.");
    }

    // ── Reset Password ────────────────────────────────────────────────────────

    @Transactional
    public ApiResponse resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getProvider() != AuthProvider.LOCAL) {
            throw new RuntimeException("Password reset is not available for OAuth2 accounts");
        }

        validateOtp(user, request.getOtp());

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setOtp(null);
        user.setOtpExpiresAt(null);
        userRepository.save(user);

        // Revoke all refresh tokens — force re-login
        refreshTokenService.revokeAllTokensForUser(user);

        log.info("Password reset for: {}", user.getEmail());
        return ApiResponse.ok("Password reset successfully. Please log in with your new password.");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AuthResponse buildAuthResponse(User user) {
        String accessToken  = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String refreshToken = refreshTokenService.createRefreshToken(user).getToken();

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpirationMs() / 1000)
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }

    private void validateOtp(User user, String inputOtp) {
        if (user.getOtp() == null || user.getOtpExpiresAt() == null) {
            throw new RuntimeException("No OTP found. Please request a new one.");
        }
        if (Instant.now().isAfter(user.getOtpExpiresAt())) {
            throw new RuntimeException("OTP has expired. Please request a new one.");
        }
        if (!user.getOtp().equals(inputOtp)) {
            throw new RuntimeException("Invalid OTP");
        }
    }

    private String generateOtp() {
        SecureRandom random = new SecureRandom();
        int otp = 100000 + random.nextInt(900000); // 6-digit
        return String.valueOf(otp);
    }
}
