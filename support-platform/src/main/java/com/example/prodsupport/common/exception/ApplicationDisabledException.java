package com.example.prodsupport.common.exception;

public class ApplicationDisabledException extends RuntimeException {

    public ApplicationDisabledException(String applicationName, String environment) {
        super("Application '" + applicationName + "' in environment '" + environment + "' is disabled and cannot be diagnosed");
    }
}
