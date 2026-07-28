package com.hacisimsek.apigateway.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * Resolves the rate-limit bucket key by client IP address.
 *
 * Resolution order:
 *   1. X-Forwarded-For header (set by reverse proxy / load balancer / ngrok)
 *   2. X-Real-IP header (set by nginx)
 *   3. Remote address from the TCP connection
 *
 * Using the first IP in X-Forwarded-For guards against proxy chains:
 *   X-Forwarded-For: <client-ip>, <proxy1-ip>, <proxy2-ip>
 *                     ^^^^^^^^^^^
 *                     We use this one
 *
 * Falls back to "unknown" if no IP can be resolved — this still gets
 * rate-limited so the gateway never crashes on missing headers.
 */
@Component("ipKeyResolver")
@Slf4j
public class IpKeyResolver implements KeyResolver {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String X_REAL_IP       = "X-Real-IP";
    private static final String FALLBACK_KEY    = "unknown";

    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();

        String ip = extractIp(request);
        log.debug("[RateLimit] Key resolved: ip={}, path={}", ip, request.getPath());
        return Mono.just(ip);
    }

    private String extractIp(ServerHttpRequest request) {

        // 1. X-Forwarded-For (most common — set by nginx, AWS ALB, ngrok)
        List<String> forwarded = request.getHeaders().get(X_FORWARDED_FOR);
        if (forwarded != null && !forwarded.isEmpty()) {
            String header = forwarded.get(0);
            if (header != null && !header.isBlank()) {
                // Take only the first IP — the original client
                String firstIp = header.split(",")[0].trim();
                if (!firstIp.isEmpty()) {
                    return firstIp;
                }
            }
        }

        // 2. X-Real-IP (set by nginx proxy_set_header X-Real-IP)
        String realIp = request.getHeaders().getFirst(X_REAL_IP);
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }

        // 3. Direct TCP connection remote address
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }

        log.warn("[RateLimit] Could not resolve client IP for path={} — using fallback key",
                request.getPath());
        return FALLBACK_KEY;
    }
}
