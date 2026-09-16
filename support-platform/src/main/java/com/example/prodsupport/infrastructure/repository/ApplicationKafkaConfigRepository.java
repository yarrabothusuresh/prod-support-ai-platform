package com.example.prodsupport.infrastructure.repository;

import com.example.prodsupport.domain.kafka.ApplicationKafkaConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ApplicationKafkaConfigRepository extends JpaRepository<ApplicationKafkaConfig, Long> {

    Optional<ApplicationKafkaConfig> findByApplicationId(Long applicationId);

    Optional<ApplicationKafkaConfig> findByApplicationApplicationNameAndApplicationEnvironment(
            String applicationName, String environment);
}
