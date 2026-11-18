package com.flowforge.workflow.validation;

import com.flowforge.workflow.dto.DependencyRequest;
import com.flowforge.workflow.dto.TaskDefinitionRequest;
import com.flowforge.workflow.dto.WorkflowDefinitionRequest;
import com.flowforge.workflow.model.DagValidationResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DagValidatorTest {

    private final DagValidator validator = new DagValidator();

    @Test
    void rejectsNullWorkflow() {
        DagValidationResult result = validator.validate(null);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).containsExactly("Workflow definition is required.");
    }

    @Test
    void rejectsEmptyTaskList() {
        DagValidationResult result = validator.validate(workflow(List.of(), List.of()));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).containsExactly("A workflow must contain at least one task.");
    }

    @Test
    void rejectsNullTaskAndDependencyElements() {
        DagValidationResult result = validator.validate(workflow(
                java.util.Arrays.asList(task("A"), null),
                java.util.Arrays.asList(edge("A", "A"), null)
        ));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).containsExactly(
                "Tasks must not contain null values.",
                "Task 'A' cannot depend on itself.",
                "Dependencies must not contain null values."
        );
    }

    @Test
    void rejectsBlankDependencyEndpoint() {
        DagValidationResult result = validator.validate(workflow(
                List.of(task("A")),
                List.of(edge(" ", "A"))
        ));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).containsExactly("Dependency source and target must not be blank.");
    }

    @Test
    void acceptsSingleNodeDag() {
        DagValidationResult result = validator.validate(workflow(List.of(task("A")), List.of()));

        assertThat(result.valid()).isTrue();
        assertThat(result.topologicalOrder()).containsExactly("A");
    }

    @Test
    void acceptsLinearDag() {
        DagValidationResult result = validator.validate(workflow(
                List.of(task("A"), task("B"), task("C")),
                List.of(edge("A", "B"), edge("B", "C"))
        ));

        assertThat(result.valid()).isTrue();
        assertThat(result.topologicalOrder()).containsExactly("A", "B", "C");
    }

    @Test
    void acceptsAndSortsDiamondDag() {
        DagValidationResult result = validator.validate(workflow(
                List.of(task("A"), task("B"), task("C"), task("D")),
                List.of(edge("A", "B"), edge("A", "C"), edge("B", "D"), edge("C", "D"))
        ));

        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.topologicalOrder()).containsExactly("A", "B", "C", "D");
    }

    @Test
    void acceptsDisconnectedDag() {
        DagValidationResult result = validator.validate(workflow(
                List.of(task("A"), task("B"), task("C"), task("D")),
                List.of(edge("A", "B"), edge("C", "D"))
        ));

        assertThat(result.valid()).isTrue();
        assertThat(result.topologicalOrder()).containsExactly("A", "C", "B", "D");
    }

    @Test
    void rejectsCycle() {
        DagValidationResult result = validator.validate(workflow(
                List.of(task("A"), task("B"), task("C")),
                List.of(edge("A", "B"), edge("B", "C"), edge("C", "A"))
        ));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).containsExactly("Workflow contains a cycle; topological ordering is impossible.");
    }

    @Test
    void rejectsCycleWithDownstreamTask() {
        DagValidationResult result = validator.validate(workflow(
                List.of(task("A"), task("B"), task("C"), task("D")),
                List.of(edge("A", "B"), edge("B", "C"), edge("C", "A"), edge("C", "D"))
        ));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).containsExactly("Workflow contains a cycle; topological ordering is impossible.");
    }

    @Test
    void rejectsDuplicateTaskKeys() {
        DagValidationResult result = validator.validate(workflow(
                List.of(task("A"), task("A")),
                List.of()
        ));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).containsExactly("Duplicate task key: 'A'.");
    }

    @Test
    void rejectsMissingDependencyEndpoint() {
        DagValidationResult result = validator.validate(workflow(
                List.of(task("A")),
                List.of(edge("A", "missing"))
        ));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).containsExactly("Dependency target 'missing' does not match a task.");
    }

    @Test
    void rejectsSelfLoopAndDuplicateDependency() {
        DagValidationResult result = validator.validate(workflow(
                List.of(task("A"), task("B")),
                List.of(edge("A", "A"), edge("A", "B"), edge("A", "B"))
        ));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).containsExactly(
                "Task 'A' cannot depend on itself.",
                "Duplicate dependency: 'A' -> 'B'."
        );
    }

    private WorkflowDefinitionRequest workflow(List<TaskDefinitionRequest> tasks, List<DependencyRequest> dependencies) {
        return new WorkflowDefinitionRequest("Test workflow", tasks, dependencies);
    }

    private TaskDefinitionRequest task(String key) {
        return new TaskDefinitionRequest(key, "LOG", null);
    }

    private DependencyRequest edge(String from, String to) {
        return new DependencyRequest(from, to);
    }
}
