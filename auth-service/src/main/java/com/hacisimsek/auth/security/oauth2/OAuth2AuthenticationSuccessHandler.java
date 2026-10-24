package com.hacisimsek.auth.security.oauth2;

import com.hacisimsek.auth.model.AuthProvider;
import com.hacisimsek.auth.model.Role;
import com.hacisimsek.auth.model.User;
import com.hacisimsek.auth.repository.UserRepository;
import com.hacisimsek.auth.security.JwtTokenProvider;
import com.hacisimsek.auth.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User oAuth2User = oauthToken.getPrincipal();
        String registrationId = oauthToken.getAuthorizedClientRegistrationId(); // "google"

        String email = oAuth2User.getAttribute("email");
        String name  = oAuth2User.getAttribute("name");
        String sub   = oAuth2User.getAttribute("sub"); // Google's unique user ID

        AuthProvider provider = AuthProvider.valueOf(registrationId.toUpperCase());

        // Find or create user
        User user = userRepository.findByEmail(email)
                .orElseGet(() -> userRepository.findByProviderAndProviderId(provider, sub)
                        .orElseGet(() -> {
                            User newUser = User.builder()
                                    .name(name)
                                    .email(email)
                                    .provider(provider)
                                    .providerId(sub)
                                    .role(Role.ROLE_USER)
                                    .emailVerified(true)   // OAuth2 providers verify email
                                    .build();
                            return userRepository.save(newUser);
                        }));

        // Update provider info if user registered via email first and is now linking Google
        if (user.getProvider() == AuthProvider.LOCAL) {
            user.setProvider(provider);
            user.setProviderId(sub);
            user.setEmailVerified(true);
            user = userRepository.save(user);
        }

        String accessToken  = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String refreshToken = refreshTokenService.createRefreshToken(user).getToken();

        // Redirect to frontend with tokens in query params
        String redirectUrl = UriComponentsBuilder.fromUriString(frontendUrl + "/oauth2/redirect")
                .queryParam("accessToken", accessToken)
                .queryParam("refreshToken", refreshToken)
                .build().toUriString();

        log.info("OAuth2 login success for user: {}, provider: {}", email, registrationId);
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}
