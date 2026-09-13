package com.example.prodsupport.ai.tools.model;

import java.util.List;

public record RecentErrorsData(
        int count,
        List<SanitizedErrorDto> errors
) {}
