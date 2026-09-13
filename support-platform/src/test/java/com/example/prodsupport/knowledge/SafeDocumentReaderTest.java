package com.example.prodsupport.knowledge;

import com.example.prodsupport.ai.config.KnowledgeProperties;
import com.example.prodsupport.knowledge.service.SafeDocumentReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SafeDocumentReaderTest {

    @TempDir
    Path tempDir;

    private SafeDocumentReader createReader(String baseDir) {
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.setBaseDirectory(baseDir);
        return new SafeDocumentReader(properties);
    }

    @Test
    @DisplayName("Should read valid file within base directory")
    void shouldReadValidFileWithinBaseDirectory() throws Exception {
        Path subDir = tempDir.resolve("runbooks");
        Files.createDirectories(subDir);
        Path validFile = subDir.resolve("db-guide.md");
        Files.writeString(validFile, "# Database Runbook\nSteps to fix connection pools.");

        SafeDocumentReader reader = createReader(tempDir.toString());
        String content = reader.readDocument("runbooks/db-guide.md");

        assertThat(content).contains("# Database Runbook");
    }

    @Test
    @DisplayName("Should reject path traversal attempts with SecurityException")
    void shouldRejectPathTraversal() {
        SafeDocumentReader reader = createReader(tempDir.toString());

        assertThatThrownBy(() -> reader.readDocument("../../../etc/passwd"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("path traversal");

        assertThatThrownBy(() -> reader.readDocument("..\\..\\secret.txt"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("path traversal");
    }

    @Test
    @DisplayName("Should reject non-existent file with IllegalArgumentException")
    void shouldRejectNonExistentFile() {
        SafeDocumentReader reader = createReader(tempDir.toString());

        assertThatThrownBy(() -> reader.readDocument("non-existent-doc.md"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }
}
