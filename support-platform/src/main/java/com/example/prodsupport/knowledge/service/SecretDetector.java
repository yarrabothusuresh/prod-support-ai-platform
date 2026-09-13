package com.example.prodsupport.knowledge.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SecretDetector {

    private static final Logger log = LoggerFactory.getLogger(SecretDetector.class);

    private static final List<Pattern> SECRET_PATTERNS = List.of(
            Pattern.compile("(?i)password\\s*[:=]\\s*['\"]?[^'\"\\s,;]{4,}"),
            Pattern.compile("(?i)secret\\s*[:=]\\s*['\"]?[^'\"\\s,;]{4,}"),
            Pattern.compile("(?i)(api[_-]?key)\\s*[:=]\\s*['\"]?[^'\"\\s,;]{4,}"),
            Pattern.compile("(?i)authorization\\s*:\\s*bearer\\s+['\"]?[a-zA-Z0-9._\\-]{10,}"),
            Pattern.compile("-----BEGIN\\s+(?:RSA\\s+)?PRIVATE\\s+KEY-----"),
            Pattern.compile("(?i)client_secret\\s*[:=]\\s*['\"]?[^'\"\\s,;]{4,}")
    );

    public boolean containsSecrets(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        for (Pattern pattern : SECRET_PATTERNS) {
            if (pattern.matcher(content).find()) {
                return true;
            }
        }
        return false;
    }

    public List<String> findSecretMatches(String content) {
        List<String> findings = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return findings;
        }
        for (Pattern pattern : SECRET_PATTERNS) {
            Matcher matcher = pattern.matcher(content);
            if (matcher.find()) {
                findings.add("Pattern matched: " + pattern.pattern());
            }
        }
        return findings;
    }
}
