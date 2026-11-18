# FlowForge database design

PostgreSQL 16 is both the source of truth and the future task queue. The executable contract is [V1__initial_schema.sql](../src/main/resources/db/migration/V1__initial_schema.sql); it was applied through Flyway before this document was marked complete.

## Definition and execution snapshots

`workflows` is the stable, mutable identity (name, enabled flag, and current version). `workflow_versions` is an editable draft until `published_at` is set. After that, triggers make the version, its `workflow_tasks`, and its `task_edges` immutable.

`workflow_runs` records a particular published version and stores its workflow-name, version, and cron snapshots. At trigger time, its task definitions are copied into `task_runs` and its definition edges are copied into `run_task_edges`. Thus an execution never needs to consult a later definition version.

```
workflows -> workflow_versions -> workflow_tasks -> task_edges
     |                 |
     +--------------> workflow_runs -> task_runs -> task_attempts
                                 |
                                 +-> run_task_edges
worker_instances ----------------> task_runs / task_attempts
```

The deferred composite foreign key from `(workflows.id, current_version)` to `(workflow_versions.workflow_id, version)` allows a workflow and its first version to be inserted atomically, but prevents a dangling current version at commit.

## Queue contract for later milestones

Roots materialize with `pending_deps = 0` and `READY`; other nodes are `PENDING`. A successful predecessor decrements a child's count in the same transaction and makes it `READY` only when it reaches zero.

Workers will use `FOR UPDATE SKIP LOCKED` against due `READY`/`RETRYING` rows. A claim increments `attempt`; completion, heartbeat, and failure updates must match `(task_run_id, locked_by, attempt)`. The attempt number therefore fences a late worker after lease recovery. `task_attempts` preserves every claim.

`max_retries` means retries after the initial claim: `attempt = 0` is unclaimed, so a task with three retries may be claimed four times. The system offers at-least-once execution, not exactly-once delivery.

## Database-enforced invariants

- A task edge cannot be duplicated, self-referential, point to a missing task, or cross definition versions.
- A run can use only a published workflow version.
- A run-task edge cannot join task runs from different workflow runs.
- A running task must have both an owning worker and an expiry/heartbeat lease.
- A cron slot is unique per workflow; manual runs are excluded from that partial unique index.
- JSON configuration and metadata are JSON objects; retry counts and dependency counts cannot be negative.

Cycles are intentionally validated in Java with Kahn's algorithm in the next step; the relational constraints cover the local structural errors above.
