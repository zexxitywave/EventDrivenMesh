package com.hacisimsek.apigateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Gateway JWT filter — validates every protected request.
 *
 * Steps:
 *  1. Extract Bearer token from Authorization header
 *  2. Parse and verify JWT signature (hex-decoded HMAC-SHA key)
 *  3. Check if the token's jti is in the Redis blacklist
 *  4. Generate X-Trace-Id (UUID) and forward it downstream alongside
 *     X-User-Id / X-User-Email / X-User-Role
 *
 * X-Trace-Id correlates a single HTTP request across all downstream services.
 * If the incoming request already carries an X-Trace-Id (e.g. from a load
 * balancer or upstream proxy), that value is preserved and forwarded as-is.
 */
@Component
@Slf4j
public class JwtAuthFilter extends AbstractGatewayFilterFactory<JwtAuthFilter.Config> {

    private static final String BLACKLIST_PREFIX = "blacklist:";

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    // Reactive Redis template — gateway is WebFlux (non-blocking)
    private final ReactiveStringRedisTemplate redisTemplate;

    public JwtAuthFilter(ReactiveStringRedisTemplate redisTemplate) {
        super(Config.class);
        this.redisTemplate = redisTemplate;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {

            ServerHttpRequest request = exchange.getRequest();
            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return unauthorized(exchange.getResponse(), "Missing or invalid Authorization header");
            }

            String token = authHeader.substring(7);

            Claims claims;
            try {
                claims = parseToken(token);
            } catch (ExpiredJwtException e) {
                log.warn("JWT expired for request: {}", request.getURI());
                return unauthorized(exchange.getResponse(), "JWT token has expired");
            } catch (MalformedJwtException | IllegalArgumentException e) {
                log.warn("Invalid JWT for request: {}", request.getURI());
                return unauthorized(exchange.getResponse(), "Invalid JWT token");
            } catch (Exception e) {
                log.error("JWT validation error: {}", e.getMessage());
                return unauthorized(exchange.getResponse(), "JWT validation failed");
            }

            // Check the Redis blacklist — non-blocking reactive check
            String jti = claims.getId();
            String blacklistKey = BLACKLIST_PREFIX + jti;

            return redisTemplate.hasKey(blacklistKey)
                    .flatMap(isBlacklisted -> {
                        if (Boolean.TRUE.equals(isBlacklisted)) {
                            log.warn("Rejected blacklisted token jti={} for {}",
                                    jti, request.getURI());
                            return unauthorized(exchange.getResponse(), "Token has been revoked");
                        }

                        // Reuse existing trace ID if present (from upstream proxy/LB),
                        // otherwise generate a fresh UUID for this request
                        String traceId = request.getHeaders().getFirst("X-Trace-Id");
                        if (traceId == null || traceId.isBlank()) {
                            traceId = UUID.randomUUID().toString();
                        }

                        ServerHttpRequest mutatedRequest = request.mutate()
                                .header("X-User-Id",    claims.getSubject())
                                .header("X-User-Email", claims.get("email", String.class))
                                .header("X-User-Role",  claims.get("role",  String.class))
                                .header("X-Trace-Id",   traceId)
                                .build();

                        return chain.filter(exchange.mutate().request(mutatedRequest).build());
                    })
                    .onErrorResume(e -> {
                        log.warn("Redis blacklist check failed — proceeding without blacklist check: {}",
                                e.getMessage());

                        String traceId = request.getHeaders().getFirst("X-Trace-Id");
                        if (traceId == null || traceId.isBlank()) {
                            traceId = UUID.randomUUID().toString();
                        }

                        ServerHttpRequest mutatedRequest = request.mutate()
                                .header("X-User-Id",    claims.getSubject())
                                .header("X-User-Email", claims.get("email", String.class))
                                .header("X-User-Role",  claims.get("role",  String.class))
                                .header("X-Trace-Id",   traceId)
                                .build();

                        return chain.filter(exchange.mutate().request(mutatedRequest).build());
                    });
        };
    }

    private Claims parseToken(String token) {
        byte[] keyBytes = hexStringToByteArray(jwtSecret);
        SecretKey key = Keys.hmacShaKeyFor(keyBytes);
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
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

    private Mono<Void> unauthorized(ServerHttpResponse response, String message) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = """
                {"success":false,"message":"%s"}
                """.formatted(message);
        DataBuffer buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    public static class Config {
    }
}
