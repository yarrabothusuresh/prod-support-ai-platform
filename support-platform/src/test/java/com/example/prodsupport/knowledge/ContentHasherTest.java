package com.example.prodsupport.knowledge;

import com.example.prodsupport.knowledge.service.ContentHasher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContentHasherTest {

    private final ContentHasher hasher = new ContentHasher();

    @Test
    @DisplayName("Computes consistent deterministic SHA-256 hash")
    void computesDeterministicSha256() {
        String content = "# Sample Runbook\nInstructions here.";
        String hash1 = hasher.computeHash(content);
        String hash2 = hasher.computeHash(content);

        assertThat(hash1).isNotBlank();
        assertThat(hash1).hasSize(64); // SHA-256 hex string is 64 chars
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("Produces different hashes for different contents")
    void producesDifferentHashesForDifferentContent() {
        String hashA = hasher.computeHash("Version 1 content");
        String hashB = hasher.computeHash("Version 2 content");

        assertThat(hashA).isNotEqualTo(hashB);
    }
}
