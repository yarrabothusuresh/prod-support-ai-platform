package com.example.prodsupport.tracing.repository;

import com.example.prodsupport.tracing.entity.ApplicationTracingConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ApplicationTracingConfigRepository extends JpaRepository<ApplicationTracingConfigEntity, Long> {

    Optional<ApplicationTracingConfigEntity> findByApplicationId(Long applicationId);

    @Query("SELECT c FROM ApplicationTracingConfigEntity c JOIN c.application a " +
           "WHERE a.applicationName = :applicationName AND a.environment = :environment")
    Optional<ApplicationTracingConfigEntity> findByApplicationNameAndEnvironment(
            @Param("applicationName") String applicationName,
            @Param("environment") String environment);
}
