package com.flowforge.workflow.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * A directed dependency: {@code from} must complete before {@code to} may run.
 */
public record DependencyRequest(
        @NotBlank(message = "Dependency source must not be blank")
        String from,

        @NotBlank(message = "Dependency target must not be blank")
        String to
) {
}
