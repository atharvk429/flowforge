package com.flowforge.workflow.dto;

import java.time.Instant;
import java.util.List;

/**
 * Consistent error representation returned by FlowForge REST APIs.
 */
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        List<String> details
) {
    public ApiErrorResponse {
        details = details == null ? List.of() : List.copyOf(details);
    }
}
