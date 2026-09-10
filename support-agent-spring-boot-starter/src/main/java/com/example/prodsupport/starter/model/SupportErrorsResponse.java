package com.example.prodsupport.starter.model;

import java.util.List;

public record SupportErrorsResponse(
        String applicationName,
        List<SupportError> errors
) {}
