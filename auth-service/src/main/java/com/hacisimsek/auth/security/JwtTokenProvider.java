package com.hacisimsek.auth.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Component
@Slf4j
public class JwtTokenProvider {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.access-token-expiration-ms}")
    private long accessTokenExpirationMs;

    private SecretKey getSigningKey() {
        // JWT_SECRET is stored as a hex string — matches api-gateway hex decoding
        byte[] keyBytes = hexStringToByteArray(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private byte[] hexStringToByteArray(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    public String generateAccessToken(UUID userId, String email, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpirationMs);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())   // jti claim — unique token ID for blacklisting
                .subject(userId.toString())
                .claim("email", email)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("Unsupported JWT: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("Malformed JWT: {}", e.getMessage());
        } catch (SecurityException e) {
            log.warn("Invalid JWT signature: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("Empty JWT claims: {}", e.getMessage());
        }
        return false;
    }

    public UUID getUserIdFromToken(String token) {
        return UUID.fromString(parseToken(token).getSubject());
    }

    public String getEmailFromToken(String token) {
        return parseToken(token).get("email", String.class);
    }

    public String getRoleFromToken(String token) {
        return parseToken(token).get("role", String.class);
    }

    /** Returns the jti (JWT ID) claim — used for blacklisting on logout */
    public String getJtiFromToken(String token) {
        return parseToken(token).getId();
    }

    /** Returns the expiry Date — used to set Redis TTL on blacklist entry */
    public Date getExpiryFromToken(String token) {
        return parseToken(token).getExpiration();
    }

    public long getAccessTokenExpirationMs() {
        return accessTokenExpirationMs;
    }
}
