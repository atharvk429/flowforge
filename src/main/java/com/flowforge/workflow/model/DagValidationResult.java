package com.flowforge.workflow.model;

import java.util.List;

/**
 * The outcome of DAG validation. A topological order is present only when the
 * workflow definition is valid.
 */
public record DagValidationResult(
        boolean valid,
        List<String> topologicalOrder,
        List<String> errors
) {
    public DagValidationResult {
        topologicalOrder = List.copyOf(topologicalOrder);
        errors = List.copyOf(errors);
    }

    public static DagValidationResult valid(List<String> topologicalOrder) {
        return new DagValidationResult(true, topologicalOrder, List.of());
    }

    public static DagValidationResult invalid(List<String> errors) {
        return new DagValidationResult(false, List.of(), errors);
    }
}
