package com.example.prodsupport.starter.store;

import com.example.prodsupport.starter.model.SupportError;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Pattern;

public class RecentErrorStore {

    private static final int DEFAULT_MAX_CAPACITY = 100;
    private static final int MAX_MESSAGE_LENGTH = 500;

    // Sanitization regex patterns
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("(?i)(password|passwd|pwd|secret)\\s*[:=]\\s*['\"]?([^\\s,;'\"]+)['\"]?");
    private static final Pattern BEARER_TOKEN_PATTERN = Pattern.compile("(?i)bearer\\s+[a-zA-Z0-9_\\-\\.]+");
    private static final Pattern TOKEN_PARAM_PATTERN = Pattern.compile("(?i)(api[_-]?key|access[_-]?token|auth[_-]?token|token)\\s*[:=]\\s*['\"]?([^\\s,;'\"]+)['\"]?");
    private static final Pattern AUTH_HEADER_PATTERN = Pattern.compile("(?i)(authorization|proxy-authorization)\\s*:\\s*(?!Bearer\\s+\\*\\*\\*REDACTED\\*\\*\\*)(?:(?:basic|digest)\\s+)?[^\\s,;'\"\\r\\n]+");
    private static final Pattern JDBC_CREDENTIAL_PATTERN = Pattern.compile("(?i)(jdbc:[a-z0-9_]+://)([^:]+):([^@]+)@");
    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile("\\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|3[47][0-9]{13}|3(?:0[0-5]|[68][0-9])[0-9]{11}|6(?:011|5[0-9]{2})[0-9]{12}|(?:2131|1800|35\\d{3})\\d{11})\\b");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");

    private final int maxCapacity;
    private final Deque<SupportError> buffer;

    public RecentErrorStore() {
        this(DEFAULT_MAX_CAPACITY);
    }

    public RecentErrorStore(int maxCapacity) {
        this.maxCapacity = maxCapacity > 0 ? maxCapacity : DEFAULT_MAX_CAPACITY;
        this.buffer = new ArrayDeque<>(this.maxCapacity);
    }

    public synchronized void recordError(String level, String type, String rawMessage) {
        recordError(new SupportError(
                Instant.now(),
                level != null ? level : "ERROR",
                type != null ? type : "ApplicationException",
                rawMessage
        ));
    }

    public synchronized void recordError(SupportError error) {
        if (error == null) {
            return;
        }

        String sanitizedMessage = sanitize(error.message());
        SupportError sanitizedError = new SupportError(
                error.timestamp() != null ? error.timestamp() : Instant.now(),
                error.level() != null ? error.level().toUpperCase() : "ERROR",
                error.type() != null ? error.type() : "ApplicationException",
                sanitizedMessage
        );

        if (buffer.size() >= maxCapacity) {
            buffer.pollFirst(); // Evict oldest
        }
        buffer.addLast(sanitizedError);
    }

    public synchronized List<SupportError> getRecentErrors(int limit) {
        int effectiveLimit = limit <= 0 ? 20 : Math.min(limit, maxCapacity);
        List<SupportError> all = new ArrayList<>(buffer);
        // Return most recent first
        int start = Math.max(0, all.size() - effectiveLimit);
        List<SupportError> result = new ArrayList<>();
        for (int i = all.size() - 1; i >= start; i--) {
            result.add(all.get(i));
        }
        return result;
    }

    public synchronized void clear() {
        buffer.clear();
    }

    public synchronized int size() {
        return buffer.size();
    }

    public static String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }

        String sanitized = message;

        // Mask credentials and secrets
        sanitized = PASSWORD_PATTERN.matcher(sanitized).replaceAll("$1=***REDACTED***");
        sanitized = BEARER_TOKEN_PATTERN.matcher(sanitized).replaceAll("Bearer ***REDACTED***");
        sanitized = TOKEN_PARAM_PATTERN.matcher(sanitized).replaceAll("$1=***REDACTED***");
        sanitized = AUTH_HEADER_PATTERN.matcher(sanitized).replaceAll("$1: ***REDACTED***");
        sanitized = JDBC_CREDENTIAL_PATTERN.matcher(sanitized).replaceAll("$1$2:***REDACTED***@");

        // Mask PII
        sanitized = CREDIT_CARD_PATTERN.matcher(sanitized).replaceAll("***REDACTED_CC***");
        sanitized = EMAIL_PATTERN.matcher(sanitized).replaceAll("***REDACTED_EMAIL***");

        // Strip multi-line stack trace: keep only first 2 lines if stack trace is present
        String[] lines = sanitized.split("\\R");
        if (lines.length > 2) {
            sanitized = lines[0] + " | " + lines[1] + " ... (stack trace suppressed)";
        }

        // Truncate to maximum length
        if (sanitized.length() > MAX_MESSAGE_LENGTH) {
            sanitized = sanitized.substring(0, MAX_MESSAGE_LENGTH) + "... (truncated)";
        }

        return sanitized;
    }
}
