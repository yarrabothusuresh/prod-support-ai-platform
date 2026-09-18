package com.example.prodsupport.logging.repository;

import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.logging.entity.ApplicationLoggingConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ApplicationLoggingConfigRepository extends JpaRepository<ApplicationLoggingConfigEntity, Long> {

    Optional<ApplicationLoggingConfigEntity> findByApplication(RegisteredApplication application);

    Optional<ApplicationLoggingConfigEntity> findByApplicationId(Long applicationId);
}
