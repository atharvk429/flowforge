package com.flowforge.workflow.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowforge.workflow.dto.DependencyRequest;
import com.flowforge.workflow.dto.TaskDefinitionRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SQL persistence operations for workflow definitions.
 */
@Repository
public class WorkflowRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public WorkflowRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Inserts a workflow's stable identity. The caller must be in the same
     * transaction that inserts its initial workflow version.
     */
    public UUID insertWorkflow(String name) {
        UUID workflowId = UUID.randomUUID();

        String sql = """
                INSERT INTO workflows (id, name, current_version)
                VALUES (?, ?, 1)
                """;

        jdbcTemplate.update(sql, workflowId, name);

        return workflowId;
    }

    /**
     * Inserts an unpublished workflow definition version. The caller publishes
     * it only after its tasks and dependency edges have been persisted.
     */
    public UUID insertWorkflowVersion(UUID workflowId, int version) {
        UUID workflowVersionId = UUID.randomUUID();

        String sql = """
                INSERT INTO workflow_versions (id, workflow_id, version)
                VALUES (?, ?, ?)
                """;

        jdbcTemplate.update(sql, workflowVersionId, workflowId, version);

        return workflowVersionId;
    }

    /**
     * Persists the task definitions of a draft workflow version and returns
     * their generated identifiers keyed by the request task key.
     */
    public Map<String, UUID> insertWorkflowTasks(
            UUID workflowVersionId,
            List<TaskDefinitionRequest> tasks
    ) {
        String sql = """
                INSERT INTO workflow_tasks (
                    workflow_version_id,
                    id,
                    task_key,
                    task_name,
                    task_type,
                    config
                )
                VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb))
                """;

        Map<String, UUID> taskIdsByKey = new LinkedHashMap<>();
        for (TaskDefinitionRequest task : tasks) {
            UUID taskId = UUID.randomUUID();
            String configJson = serializeConfig(task);

            jdbcTemplate.update(
                    sql,
                    workflowVersionId,
                    taskId,
                    task.taskKey(),
                    task.taskKey(),
                    task.type(),
                    configJson
            );
            taskIdsByKey.put(task.taskKey(), taskId);
        }

        return taskIdsByKey;
    }

    private String serializeConfig(TaskDefinitionRequest task) {
        if (task.config() == null) {
            return "{}";
        }

        try {
            return objectMapper.writeValueAsString(task.config());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Unable to serialize configuration for task '" + task.taskKey() + "'.",
                    exception
            );
        }
    }

    /**
     * Persists the dependency edges for a draft workflow version.
     */
    public void insertWorkflowEdges(
            UUID workflowVersionId,
            List<DependencyRequest> dependencies,
            Map<String, UUID> taskIdsByKey
    ) {
        String sql = """
                INSERT INTO task_edges (
                    workflow_version_id,
                    from_task_id,
                    to_task_id
                )
                VALUES (?, ?, ?)
                """;

        for (DependencyRequest dependency : dependencies) {
            UUID fromTaskId = taskIdsByKey.get(dependency.from());
            if (fromTaskId == null) {
                throw new IllegalArgumentException(
                        "No task ID mapping exists for dependency source '" + dependency.from() + "'."
                );
            }

            UUID toTaskId = taskIdsByKey.get(dependency.to());
            if (toTaskId == null) {
                throw new IllegalArgumentException(
                        "No task ID mapping exists for dependency target '" + dependency.to() + "'."
                );
            }

            jdbcTemplate.update(sql, workflowVersionId, fromTaskId, toTaskId);
        }
    }

    /**
     * Publishes a draft version after all of its task definitions and edges
     * have been inserted. Published versions are immutable in the database.
     */
    public void publishWorkflowVersion(UUID workflowVersionId) {
        String sql = """
                UPDATE workflow_versions
                SET published_at = now()
                WHERE id = ?
                  AND published_at IS NULL
                """;

        int updatedRows = jdbcTemplate.update(sql, workflowVersionId);
        if (updatedRows == 0) {
            throw new IllegalStateException(
                    "Workflow version '" + workflowVersionId + "' does not exist or is already published."
            );
        }
    }
}
