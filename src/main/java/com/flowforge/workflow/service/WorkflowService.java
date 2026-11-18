package com.flowforge.workflow.service;

import com.flowforge.workflow.dto.TaskDefinitionRequest;
import com.flowforge.workflow.dto.WorkflowCreatedResponse;
import com.flowforge.workflow.dto.WorkflowDefinitionRequest;
import com.flowforge.workflow.repository.WorkflowRepository;
import com.flowforge.workflow.validation.DagValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Coordinates validation and persistence of workflow definitions.
 */
@Service
public class WorkflowService {

    private static final Set<String> SUPPORTED_TASK_TYPES = Set.of(
            "HTTP",
            "DELAY",
            "LOG",
            "FAIL_N_TIMES"
    );

    private final DagValidator dagValidator;
    private final WorkflowRepository workflowRepository;

    public WorkflowService(DagValidator dagValidator, WorkflowRepository workflowRepository) {
        this.dagValidator = dagValidator;
        this.workflowRepository = workflowRepository;
    }

    @Transactional
    public WorkflowCreatedResponse createWorkflow(WorkflowDefinitionRequest request) {
        var dagResult = dagValidator.validate(request);
        if (!dagResult.valid()) {
            throw new IllegalArgumentException(String.join(" ", dagResult.errors()));
        }

        List<String> taskErrors = validateTaskTypesAndConfigs(request);
        if (!taskErrors.isEmpty()) {
            throw new IllegalArgumentException(String.join(" ", taskErrors));
        }

        var workflowId = workflowRepository.insertWorkflow(request.name());
        var workflowVersionId = workflowRepository.insertWorkflowVersion(workflowId, 1);
        var taskIdsByKey = workflowRepository.insertWorkflowTasks(workflowVersionId, request.tasks());

        workflowRepository.insertWorkflowEdges(
                workflowVersionId,
                request.dependencies(),
                taskIdsByKey
        );
        workflowRepository.publishWorkflowVersion(workflowVersionId);

        return new WorkflowCreatedResponse(
                workflowId,
                request.name(),
                1,
                request.tasks().size(),
                request.dependencies().size()
        );
    }

    private List<String> validateTaskTypesAndConfigs(WorkflowDefinitionRequest request) {
        List<String> errors = new ArrayList<>();

        for (TaskDefinitionRequest task : request.tasks()) {
            if (!SUPPORTED_TASK_TYPES.contains(task.type())) {
                errors.add("Unsupported task type '" + task.type() + "' for task '" + task.taskKey() + "'.");
            }
            if (task.config() != null && !task.config().isObject()) {
                errors.add("Configuration for task '" + task.taskKey() + "' must be a JSON object.");
            }
        }

        return errors;
    }
}
