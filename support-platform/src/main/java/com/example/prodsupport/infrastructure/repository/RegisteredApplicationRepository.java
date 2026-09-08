package com.example.prodsupport.infrastructure.repository;

import com.example.prodsupport.domain.RegisteredApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RegisteredApplicationRepository extends JpaRepository<RegisteredApplication, Long> {

    Optional<RegisteredApplication> findByApplicationNameAndEnvironment(String applicationName, String environment);

    boolean existsByApplicationNameAndEnvironment(String applicationName, String environment);
}
