package com.flowforge.workflow.validation;

import com.flowforge.workflow.dto.DependencyRequest;
import com.flowforge.workflow.dto.TaskDefinitionRequest;
import com.flowforge.workflow.dto.WorkflowDefinitionRequest;
import com.flowforge.workflow.model.DagValidationResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Validates a workflow's dependency graph and produces a deterministic
 * topological order using Kahn's algorithm.
 */
public class DagValidator {

    public DagValidationResult validate(WorkflowDefinitionRequest workflow) {
        if (workflow == null) {
            return DagValidationResult.invalid(List.of("Workflow definition is required."));
        }

        List<String> errors = new ArrayList<>();
        Map<String, List<String>> adjacencyList = new LinkedHashMap<>();
        Map<String, Integer> indegree = new LinkedHashMap<>();

        addTasks(workflow.tasks(), adjacencyList, indegree, errors);
        addDependencies(workflow.dependencies(), adjacencyList, indegree, errors);

        if (!errors.isEmpty()) {
            return DagValidationResult.invalid(errors);
        }

        List<String> topologicalOrder = topologicalSort(adjacencyList, indegree);
        if (topologicalOrder.size() != adjacencyList.size()) {
            return DagValidationResult.invalid(List.of(
                    "Workflow contains a cycle; topological ordering is impossible."
            ));
        }

        return DagValidationResult.valid(topologicalOrder);
    }

    private void addTasks(
            List<TaskDefinitionRequest> tasks,
            Map<String, List<String>> adjacencyList,
            Map<String, Integer> indegree,
            List<String> errors
    ) {
        if (tasks == null) {
            errors.add("Tasks are required.");
            return;
        }
        if (tasks.isEmpty()) {
            errors.add("A workflow must contain at least one task.");
            return;
        }

        for (TaskDefinitionRequest task : tasks) {
            if (task == null) {
                errors.add("Tasks must not contain null values.");
                continue;
            }

            String taskKey = task.taskKey();
            if (taskKey == null || taskKey.isBlank()) {
                errors.add("Task key must not be blank.");
                continue;
            }

            if (adjacencyList.containsKey(taskKey)) {
                errors.add("Duplicate task key: '" + taskKey + "'.");
                continue;
            }

            adjacencyList.put(taskKey, new ArrayList<>());
            indegree.put(taskKey, 0);
        }
    }

    private void addDependencies(
            List<DependencyRequest> dependencies,
            Map<String, List<String>> adjacencyList,
            Map<String, Integer> indegree,
            List<String> errors
    ) {
        if (dependencies == null) {
            errors.add("Dependencies are required; use an empty list when there are none.");
            return;
        }

        Set<Edge> edges = new LinkedHashSet<>();
        for (DependencyRequest dependency : dependencies) {
            if (dependency == null) {
                errors.add("Dependencies must not contain null values.");
                continue;
            }

            String from = dependency.from();
            String to = dependency.to();
            if (from == null || from.isBlank() || to == null || to.isBlank()) {
                errors.add("Dependency source and target must not be blank.");
                continue;
            }

            boolean sourceExists = adjacencyList.containsKey(from);
            boolean targetExists = adjacencyList.containsKey(to);

            if (!sourceExists) {
                errors.add("Dependency source '" + from + "' does not match a task.");
            }
            if (!targetExists) {
                errors.add("Dependency target '" + to + "' does not match a task.");
            }
            if (from.equals(to)) {
                errors.add("Task '" + from + "' cannot depend on itself.");
            }

            if (!sourceExists || !targetExists || from.equals(to)) {
                continue;
            }

            Edge edge = new Edge(from, to);
            if (!edges.add(edge)) {
                errors.add("Duplicate dependency: '" + from + "' -> '" + to + "'.");
                continue;
            }

            adjacencyList.get(from).add(to);
            indegree.compute(to, (key, value) -> value + 1);
        }
    }

    private List<String> topologicalSort(
            Map<String, List<String>> adjacencyList,
            Map<String, Integer> indegree
    ) {
        Map<String, Integer> remainingIndegree = new LinkedHashMap<>(indegree);
        Queue<String> readyTasks = new ArrayDeque<>();

        for (Map.Entry<String, Integer> entry : remainingIndegree.entrySet()) {
            if (entry.getValue() == 0) {
                readyTasks.add(entry.getKey());
            }
        }

        List<String> order = new ArrayList<>();
        while (!readyTasks.isEmpty()) {
            String taskKey = readyTasks.remove();
            order.add(taskKey);

            for (String dependentTask : adjacencyList.get(taskKey)) {
                int updatedIndegree = remainingIndegree.compute(dependentTask, (key, value) -> value - 1);
                if (updatedIndegree == 0) {
                    readyTasks.add(dependentTask);
                }
            }
        }

        return order;
    }

    private record Edge(String from, String to) {
    }
}
