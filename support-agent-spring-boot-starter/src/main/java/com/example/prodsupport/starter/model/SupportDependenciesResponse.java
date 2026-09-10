package com.example.prodsupport.starter.model;

import java.util.List;

public record SupportDependenciesResponse(
        String applicationName,
        List<SupportDependency> dependencies
) {}
