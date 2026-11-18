package com.flowforge.workflow.dto;

import java.util.UUID;

/**
 * The public representation of a successfully created workflow definition.
 */
public record WorkflowCreatedResponse(
        UUID workflowId,
        String name,
        int version,
        int taskCount,
        int dependencyCount
) {
}
