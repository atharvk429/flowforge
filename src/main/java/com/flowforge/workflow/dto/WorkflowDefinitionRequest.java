package com.flowforge.workflow.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * The workflow definition submitted by a client.
 */
public record WorkflowDefinitionRequest(
        @NotBlank(message = "Workflow name must not be blank")
        String name,

        @NotNull(message = "Tasks are required")
        @Size(min = 1, message = "A workflow must contain at least one task")
        List<@NotNull @Valid TaskDefinitionRequest> tasks,

        @NotNull(message = "Dependencies are required; use an empty array when there are none")
        List<@NotNull @Valid DependencyRequest> dependencies
) {
}
