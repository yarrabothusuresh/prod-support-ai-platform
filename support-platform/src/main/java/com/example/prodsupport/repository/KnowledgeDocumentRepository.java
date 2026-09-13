package com.example.prodsupport.repository;

import com.example.prodsupport.domain.DocumentType;
import com.example.prodsupport.domain.KnowledgeDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {

    List<KnowledgeDocument> findByApplicationNameAndEnvironment(String applicationName, String environment);

    List<KnowledgeDocument> findByApplicationNameAndEnvironmentAndEnabledTrue(String applicationName, String environment);

    List<KnowledgeDocument> findByApplicationNameAndEnvironmentAndDocumentTypeInAndEnabledTrue(
            String applicationName, String environment, Collection<DocumentType> documentTypes);

    Optional<KnowledgeDocument> findByApplicationNameAndEnvironmentAndSource(
            String applicationName, String environment, String source);

    long countByApplicationNameAndEnvironment(String applicationName, String environment);
}
