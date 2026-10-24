package com.hacisimsek.logging.repository;

import com.hacisimsek.logging.model.AlertRule;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.UUID;

public interface AlertRuleRepository extends MongoRepository<AlertRule, UUID> {

    List<AlertRule> findByEnabled(boolean enabled);

    List<AlertRule> findByServiceNameAndEnabled(String serviceName, boolean enabled);
}
