package com.example.prodsupport.common.exception;

public class ApplicationNotFoundException extends RuntimeException {

    public ApplicationNotFoundException(Long id) {
        super(String.format("Application with id '%d' not found", id));
    }

    public ApplicationNotFoundException(String applicationName, String environment) {
        super(String.format("Application %s in environment %s is not registered", applicationName, environment));
    }
}
