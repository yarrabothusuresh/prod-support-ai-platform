package com.example.prodsupport.logging.sanitizer;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class LogSanitizer {

    private static final Pattern BEARER_TOKEN_PATTERN = Pattern.compile("(?i)(bearer\\s+)[a-zA-Z0-9._~+/-]{10,}", Pattern.CASE_INSENSITIVE);
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("(?i)(password|passwd|pwd|secret)\\s*[:=]\\s*['\"]?[^\\s,'\"]+['\"]?");
    private static final Pattern API_KEY_PATTERN = Pattern.compile("(?i)(api[_-]?key|access[_-]?token|client[_-]?secret)\\s*[:=]\\s*['\"]?[^\\s,'\"]+['\"]?");
    private static final Pattern PRIVATE_KEY_PATTERN = Pattern.compile("(?s)-----BEGIN[ A-Z_-]*PRIVATE KEY-----.*?-----END[ A-Z_-]*PRIVATE KEY-----");
    private static final Pattern JWT_PATTERN = Pattern.compile("eyJ[a-zA-Z0-9_-]{10,}\\.eyJ[a-zA-Z0-9_-]{10,}\\.[a-zA-Z0-9_-]{10,}");
    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile("\\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|3[47][0-9]{13})\\b");

    public String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return message;
        }

        String result = message;
        result = PRIVATE_KEY_PATTERN.matcher(result).replaceAll("[REDACTED_PRIVATE_KEY]");
        result = JWT_PATTERN.matcher(result).replaceAll("[REDACTED_JWT]");
        result = BEARER_TOKEN_PATTERN.matcher(result).replaceAll("$1[REDACTED_TOKEN]");
        result = PASSWORD_PATTERN.matcher(result).replaceAll("$1=[REDACTED_PASSWORD]");
        result = API_KEY_PATTERN.matcher(result).replaceAll("$1=[REDACTED_API_KEY]");
        result = CREDIT_CARD_PATTERN.matcher(result).replaceAll("[REDACTED_CARD_NUMBER]");

        return result;
    }
}
