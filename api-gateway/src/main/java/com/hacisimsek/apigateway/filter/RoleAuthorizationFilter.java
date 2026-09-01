package com.hacisimsek.apigateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Gateway filter that enforces role-based access control on individual routes.
 *
 * Usage in application.yml:
 * <pre>
 *   filters:
 *     - JwtAuthFilter                      # validates token, injects X-User-Role
 *     - name: RoleAuthorizationFilter
 *       args:
 *         roles: ROLE_ADMIN                # comma-separated list of allowed roles
 * </pre>
 *
 * The JwtAuthFilter must run first — it injects the X-User-Role header which
 * this filter reads. If the role is not in the allowed list, a 403 is returned
 * immediately without forwarding to the downstream service.
 *
 * Role values match the Role enum in auth-service: ROLE_USER, ROLE_SELLER, ROLE_ADMIN.
 */
@Component
@Slf4j
public class RoleAuthorizationFilter
        extends AbstractGatewayFilterFactory<RoleAuthorizationFilter.Config> {

    public RoleAuthorizationFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String userRole = exchange.getRequest().getHeaders().getFirst("X-User-Role");

            if (userRole == null || userRole.isBlank()) {
                log.warn("[RoleAuth] X-User-Role header missing — JwtAuthFilter must run first");
                return forbidden(exchange.getResponse(), "Access denied — role not determined");
            }

            List<String> allowed = config.getRoles();
            if (!allowed.contains(userRole)) {
                log.warn("[RoleAuth] Role '{}' not in allowed list {} for path {}",
                        userRole, allowed, exchange.getRequest().getURI().getPath());
                return forbidden(exchange.getResponse(),
                        "Access denied — required role: " + String.join(" or ", allowed));
            }

            return chain.filter(exchange);
        };
    }

    private Mono<Void> forbidden(ServerHttpResponse response, String message) {
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = """
                {"success":false,"message":"%s"}
                """.formatted(message);
        DataBuffer buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public List<String> shortcutFieldOrder() {
        return List.of("roles");
    }

    public static class Config {
        /** Comma-separated allowed roles, e.g. "ROLE_ADMIN" or "ROLE_ADMIN,ROLE_SELLER" */
        private List<String> roles;

        public List<String> getRoles() {
            return roles;
        }

        public void setRoles(String rolesStr) {
            this.roles = Arrays.stream(rolesStr.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
    }
}
