package com.example.prodsupport.database.repository;

import com.example.prodsupport.database.entity.ApplicationDatabaseConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ApplicationDatabaseConfigRepository extends JpaRepository<ApplicationDatabaseConfigEntity, Long> {

    Optional<ApplicationDatabaseConfigEntity> findByApplicationId(Long applicationId);

    Optional<ApplicationDatabaseConfigEntity> findByApplicationApplicationNameAndApplicationEnvironment(
            String applicationName, String environment);
}
