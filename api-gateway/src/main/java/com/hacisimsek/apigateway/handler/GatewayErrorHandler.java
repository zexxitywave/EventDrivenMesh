package com.hacisimsek.apigateway.handler;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayErrorHandler implements WebExceptionHandler {

    private static final Map<Integer, String> ERROR_MESSAGES = Map.ofEntries(
            Map.entry(400, "Bad request. Please check your request and try again."),
            Map.entry(401, "Unauthorized. Please sign in to continue."),
            Map.entry(403, "Forbidden. You do not have permission."),
            Map.entry(404, "Page not found."),
            Map.entry(429, "Too many requests. Please try again shortly."),
            Map.entry(500, "Internal server error. Please try again."),
            Map.entry(502, "Bad gateway. Please try again."),
            Map.entry(503, "Service unavailable. Please try again later."),
            Map.entry(504, "Gateway timeout. Please try again.")
    );

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        int statusCode;
        if (ex instanceof org.springframework.web.server.ResponseStatusException rse) {
            statusCode = rse.getStatusCode().value();
        } else {
            statusCode = HttpStatus.INTERNAL_SERVER_ERROR.value();
        }

        String message = ERROR_MESSAGES.getOrDefault(statusCode,
                "Internal server error. Please try again.");
        String html = buildHtml(statusCode, message);
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);

        response.setStatusCode(HttpStatus.valueOf(statusCode));
        response.getHeaders().setContentType(MediaType.TEXT_HTML);
        response.getHeaders().setContentLength(bytes.length);

        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }

    private String buildHtml(int code, String message) {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="robots" content="noindex">
    <title>%1$d - Error</title>
    <style>
        body { font-family: system-ui, sans-serif; background: #fff; color: #333; display: flex; height: 100vh; align-items: center; justify-content: center; margin: 0; }
        .box { text-align: center; }
        h1 { font-size: 4rem; margin: 0; color: #dc3545; }
        p  { font-size: 1.1rem; color: #666; margin: 8px 0; }
        a  { color: #007bff; text-decoration: none; }
        a:hover { text-decoration: underline; }
    </style>
</head>
<body>
    <div class="box">
        <h1>%1$d</h1>
        <p>%2$s</p>
        <p><a href="/">Back to Home</a></p>
    </div>
</body>
</html>
""".formatted(code, message);
    }
}