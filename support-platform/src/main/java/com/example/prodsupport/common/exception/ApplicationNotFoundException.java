package com.example.prodsupport.common.exception;

public class ApplicationNotFoundException extends RuntimeException {

    public ApplicationNotFoundException(Long id) {
        super(String.format("Application with id '%d' not found", id));
    }
}
