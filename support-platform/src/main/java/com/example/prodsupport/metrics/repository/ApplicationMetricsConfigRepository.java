package com.example.prodsupport.metrics.repository;

import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.metrics.entity.ApplicationMetricsConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ApplicationMetricsConfigRepository extends JpaRepository<ApplicationMetricsConfigEntity, Long> {

    Optional<ApplicationMetricsConfigEntity> findByApplication(RegisteredApplication application);

    Optional<ApplicationMetricsConfigEntity> findByApplicationId(Long applicationId);

    Optional<ApplicationMetricsConfigEntity> findByApplication_ApplicationNameAndApplication_Environment(String applicationName, String environment);

    void deleteByApplication(RegisteredApplication application);
}
