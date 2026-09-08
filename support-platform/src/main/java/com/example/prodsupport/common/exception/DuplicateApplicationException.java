package com.example.prodsupport.common.exception;

public class DuplicateApplicationException extends RuntimeException {

    public DuplicateApplicationException(String applicationName, String environment) {
        super(String.format("Application '%s' is already registered for environment '%s'", applicationName, environment));
    }
}
