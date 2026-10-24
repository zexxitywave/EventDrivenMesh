package com.hacisimsek.apigateway.filter;

import com.hacisimsek.common.logging.LogPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
@ConditionalOnBean(LogPublisher.class)
@RequiredArgsConstructor
@Slf4j
public class AccessLogFilter implements GlobalFilter, Ordered {

    private static final String SERVICE_NAME = "api-gateway";

    private final LogPublisher logPublisher;

    @Override
    public int getOrder() {
        // Run very early so we capture total latency including JwtAuthFilter + routing
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startMs = System.currentTimeMillis();
        ServerHttpRequest request = exchange.getRequest();

        String method  = request.getMethod().name();
        String path    = request.getURI().getPath();
        String traceId = request.getHeaders().getFirst("X-Trace-Id");
        String userId  = request.getHeaders().getFirst("X-User-Id");

        return chain.filter(exchange)
                .doFinally(signalType -> {
                    ServerHttpResponse response = exchange.getResponse();
                    long durationMs = System.currentTimeMillis() - startMs;
                    int statusCode = response.getStatusCode() != null
                            ? response.getStatusCode().value()
                            : 0;

                    String level = statusCode >= 500 ? "ERROR"
                            : statusCode >= 400 ? "WARN"
                            : "INFO";

                    String message = String.format("%s %s → %d (%dms)", method, path, statusCode, durationMs);

                    Map<String, Object> metadata = new java.util.LinkedHashMap<>();
                    metadata.put("method",    method);
                    metadata.put("path",      path);
                    metadata.put("durationMs", durationMs);
                    if (userId != null) metadata.put("userId", userId);

                    // Use the appropriate log level based on response status
                    switch (level) {
                        case "ERROR" -> logPublisher.error(SERVICE_NAME, traceId, message, metadata);
                        case "WARN"  -> logPublisher.warn(SERVICE_NAME, traceId, message, metadata);
                        default      -> logPublisher.info(SERVICE_NAME, traceId, message, metadata);
                    }

                    // Also log locally at DEBUG so developers see it in console
                    log.debug("[ACCESS] {} {} {} {}ms traceId={} userId={}",
                            method, path, statusCode, durationMs, traceId, userId);
                });
    }
}
