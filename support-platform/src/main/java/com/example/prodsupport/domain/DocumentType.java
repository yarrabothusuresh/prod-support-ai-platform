package com.example.prodsupport.domain;

public enum DocumentType {
    RUNBOOK,
    ARCHITECTURE,
    INCIDENT,
    RCA,
    TROUBLESHOOTING,
    PROCEDURE,
    OTHER;

    public static DocumentType fromString(String value) {
        if (value == null || value.isBlank()) {
            return OTHER;
        }
        try {
            return DocumentType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return OTHER;
        }
    }
}
