package com.flowforge.workflow.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

/**
 * One task in an incoming workflow definition.
 */
public record TaskDefinitionRequest(
        @NotBlank(message = "Task key must not be blank")
        String taskKey,

        @NotBlank(message = "Task type must not be blank")
        String type,

        JsonNode config
) {
}
