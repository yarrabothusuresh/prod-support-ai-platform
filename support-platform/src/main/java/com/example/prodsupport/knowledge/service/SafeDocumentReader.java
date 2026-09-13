package com.example.prodsupport.knowledge.service;

import com.example.prodsupport.ai.config.KnowledgeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class SafeDocumentReader {

    private static final Logger log = LoggerFactory.getLogger(SafeDocumentReader.class);

    private final KnowledgeProperties properties;

    public SafeDocumentReader(KnowledgeProperties properties) {
        this.properties = properties;
    }

    public String readDocument(String relativeOrApprovedPath) throws IOException {
        Path resolvedPath = resolveAndValidatePath(relativeOrApprovedPath);
        if (!Files.exists(resolvedPath) || !Files.isRegularFile(resolvedPath)) {
            throw new IllegalArgumentException("Document file not found: " + relativeOrApprovedPath);
        }
        return Files.readString(resolvedPath, StandardCharsets.UTF_8);
    }

    public Path resolveAndValidatePath(String inputPath) {
        if (inputPath == null || inputPath.isBlank()) {
            throw new IllegalArgumentException("Document source path cannot be blank");
        }

        // Reject explicit path traversal tokens
        if (inputPath.contains("..") || inputPath.startsWith("/") || inputPath.matches("^[a-zA-Z]:\\\\.*")) {
            // Check if it's an absolute path that actually starts with the normalized base directory
            Path candidate = Paths.get(inputPath).toAbsolutePath().normalize();
            Path baseDir = Paths.get(properties.getBaseDirectory()).toAbsolutePath().normalize();
            if (!candidate.startsWith(baseDir)) {
                log.warn("Path traversal attempt detected and blocked: {}", inputPath);
                throw new SecurityException("Access denied: path traversal detected or path outside base directory: " + inputPath);
            }
            return candidate;
        }

        Path baseDir = Paths.get(properties.getBaseDirectory()).toAbsolutePath().normalize();
        Path candidate;

        // If inputPath starts with the baseDirectory name itself (e.g. "documents/runbooks/..."), strip prefix
        String baseName = Paths.get(properties.getBaseDirectory()).getFileName().toString();
        if (inputPath.startsWith(baseName + "/") || inputPath.startsWith(baseName + "\\")) {
            candidate = baseDir.resolve(inputPath.substring(baseName.length() + 1)).normalize();
        } else if (inputPath.startsWith("./" + baseName + "/") || inputPath.startsWith(".\\" + baseName + "\\")) {
            candidate = baseDir.resolve(inputPath.substring(baseName.length() + 3)).normalize();
        } else {
            candidate = baseDir.resolve(inputPath).normalize();
        }

        if (!candidate.startsWith(baseDir)) {
            log.warn("Path traversal attempt detected and blocked: {}", inputPath);
            throw new SecurityException("Access denied: path outside base directory: " + inputPath);
        }

        return candidate;
    }

    public Path getBaseDirectoryPath() {
        return Paths.get(properties.getBaseDirectory()).toAbsolutePath().normalize();
    }
}
