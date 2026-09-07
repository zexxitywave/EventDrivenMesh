package com.hacisimsek.auth.controller;

import com.hacisimsek.auth.dto.*;
import com.hacisimsek.auth.repository.UserRepository;
import com.hacisimsek.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {
//
    private final AuthService authService;
    private final UserRepository userRepository;

    /**
     * Register a new user with email + password.
     * Sends OTP for email verification.
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    /**
     * Verify email using OTP sent after registration.
     */
    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        return ResponseEntity.ok(authService.verifyEmail(request));
    }

    /**
     * Resend verification OTP if user didn't receive it or it expired.
     */
    @PostMapping("/resend-verification")
    public ResponseEntity<ApiResponse> resendVerificationOtp(@RequestParam String email) {
        return ResponseEntity.ok(authService.resendVerificationOtp(email));
    }

    /**
     * Login with email + password.
     * Returns JWT access token + refresh token.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * Exchange refresh token for a new access token + new refresh token.
     * Old refresh token is revoked (token rotation).
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refreshToken(request));
    }

    /**
     * Logout Ã¢â‚¬â€ revokes the refresh token AND blacklists the access token in Redis.
     * The access token is passed in the Authorization header (Bearer <token>).
     * After this call, both tokens are immediately invalid.
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody RefreshTokenRequest request) {
        String accessToken = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            accessToken = authHeader.substring(7);
        }
        return ResponseEntity.ok(authService.logout(request.getRefreshToken(), accessToken));
    }

    /**
     * Request password reset OTP.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.ok(authService.forgotPassword(request));
    }

    /**
     * Reset password using OTP.
     */
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(authService.resetPassword(request));
    }

    /**
     * Protected endpoint Ã¢â‚¬â€ get current user info from JWT.
     * Example: used by frontend after login to show user profile.
     */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        if (userId != null && !userId.isBlank()) {
            return userRepository.findById(java.util.UUID.fromString(userId))
                    .map(user -> ResponseEntity.ok((Object) java.util.Map.of(
                            "userId",   user.getId(),
                            "name",     user.getName(),
                            "email",    user.getEmail(),
                            "role",     user.getRole().name(),
                            "provider", user.getProvider().name(),
                            "verified", user.isEmailVerified()
                    )))
                    .orElse(ResponseEntity.notFound().build());
        }
        return ResponseEntity.badRequest().body(
                java.util.Map.of("success", false, "message", "User ID not found in request"));
    }

    /**
     * OAuth2 login is handled by Spring Security OAuth2 client.
     * Redirect user to: GET /api/auth/oauth2/authorize/google
     * Callback: /api/auth/oauth2/callback/google (handled by OAuth2AuthenticationSuccessHandler)
     */
}
