package com.flowforge.workflow.controller;

import com.flowforge.workflow.dto.WorkflowCreatedResponse;
import com.flowforge.workflow.dto.WorkflowDefinitionRequest;
import com.flowforge.workflow.service.WorkflowService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * HTTP entry point for workflow-definition operations.
 */
@RestController
@RequestMapping("/api/v1/workflows")
public class WorkflowController {

    private final WorkflowService workflowService;

    public WorkflowController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @PostMapping
    public ResponseEntity<WorkflowCreatedResponse> createWorkflow(
            @Valid @RequestBody WorkflowDefinitionRequest request
    ) {
        WorkflowCreatedResponse response = workflowService.createWorkflow(request);
        URI location = URI.create("/api/v1/workflows/" + response.workflowId());

        return ResponseEntity.created(location).body(response);
    }
}
