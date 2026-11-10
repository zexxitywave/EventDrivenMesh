package com.flashsale;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Smoke test — verifies the Spring application context loads correctly.
 *
 * Infrastructure (PostgreSQL, Redis, Kafka) is disabled here so the test
 * passes without live Docker containers.  Integration tests that exercise
 * real I/O should use @SpringBootTest with a running environment or
 * Testcontainers.
 */
@SpringBootTest
@TestPropertySource(properties = {
        // Use H2 in-memory instead of PostgreSQL
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        // Disable Redis auto-connect
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=16379",
        // Point Kafka at a non-existent broker — auto-config will not fail at context load
        "spring.kafka.bootstrap-servers=localhost:19095",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
})
class FlashsaleApplicationTests {

    @Test
    void contextLoads() {
        // Verifies the Spring context starts without errors
    }
}
