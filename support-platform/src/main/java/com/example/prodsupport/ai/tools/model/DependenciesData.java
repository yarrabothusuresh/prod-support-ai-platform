package com.example.prodsupport.ai.tools.model;

import java.util.List;

public record DependenciesData(
        List<DependencyItemDto> dependencies
) {}
