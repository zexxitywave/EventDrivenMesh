package com.hacisimsek.common.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.common.Node;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * Custom Kafka health indicator exposed at /actuator/health/kafka.
 *
 * Checks:
 *  1. Can connect to the Kafka broker cluster
 *  2. At least one broker node is available
 *
 * Only activates in services that have a KafkaAdmin bean configured
 * (i.e. services that use Kafka). Non-Kafka services (cart, product, user)
 * are unaffected via @ConditionalOnBean.
 *
 * The AdminClient is reused from the KafkaAdmin bean (already configured
 * in each service's KafkaConfig). A 3-second timeout prevents blocking
 * the health check thread on a slow broker.
 */
@Component
@ConditionalOnBean(KafkaAdmin.class)
@RequiredArgsConstructor
@Slf4j
public class KafkaConsumerHealthIndicator implements HealthIndicator {

    private static final int TIMEOUT_SECONDS = 3;

    private final KafkaAdmin kafkaAdmin;

    @Override
    public Health health() {
        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {

            DescribeClusterOptions options = new DescribeClusterOptions()
                    .timeoutMs((int) TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));

            DescribeClusterResult cluster = adminClient.describeCluster(options);

            String clusterId = cluster.clusterId().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            Collection<Node> nodes = cluster.nodes().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (nodes == null || nodes.isEmpty()) {
                return Health.down()
                        .withDetail("reason", "No Kafka broker nodes available")
                        .build();
            }

            return Health.up()
                    .withDetail("clusterId", clusterId)
                    .withDetail("brokerCount", nodes.size())
                    .withDetail("brokers", nodes.stream()
                            .map(n -> n.host() + ":" + n.port())
                            .toList())
                    .build();

        } catch (Exception e) {
            log.warn("[KafkaHealth] Kafka health check failed: {}", e.getMessage());
            return Health.down()
                    .withDetail("reason", "Cannot connect to Kafka: " + e.getMessage())
                    .build();
        }
    }
}
