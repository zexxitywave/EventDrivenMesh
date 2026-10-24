package com.hacisimsek.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Shared OpenAPI/Swagger configuration for all microservices.
 *
 * Each service automatically gets:
 * - Swagger UI at /swagger-ui.html
 * - OpenAPI JSON at /v3/api-docs
 * - JWT Bearer authentication scheme in the UI
 *
 * ConditionalOnClass ensures this config only activates when
 * springdoc-openapi is on the classpath (servlet-based services).
 * api-gateway provides its own config (WebFlux variant).
 */
@Configuration
@ConditionalOnClass(name = "org.springdoc.core.models.GroupedOpenApi")
public class OpenApiConfig {

    @Value("${spring.application.name:microservice}")
    private String applicationName;

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title(formatTitle(applicationName))
                        .description("REST API documentation for " + formatTitle(applicationName))
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Zexxity Team")
                                .url("https://github.com/zexxitywave/EventDrivenMesh"))
                        .license(new License()
                                .name("MIT")
                                .url("https://opensource.org/licenses/MIT")))
                // Register JWT Bearer as a global security scheme
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Enter the JWT token obtained from /api/auth/login")));
    }

    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("public")
                .pathsToMatch("/api/**")
                .build();
    }

    private String formatTitle(String name) {
        // "order-service" -> "Order Service"
        return java.util.Arrays.stream(name.split("[-_]"))
                .map(w -> Character.toUpperCase(w.charAt(0)) + w.substring(1))
                .collect(java.util.stream.Collectors.joining(" "));
    }
}
