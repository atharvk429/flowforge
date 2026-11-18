# FlowForge — Final 7-Week Implementation Roadmap (+1 Buffer Week)

Master project plan. Update a checkbox only after the feature is implemented and tested. Do not record benchmark numbers or delivery guarantees that have not been measured.

**FlowForge** is a distributed DAG-based workflow orchestration engine: a lightweight workflow runner in the spirit of Apache Airflow, built with Java 21, Spring Boot, and PostgreSQL.

- **Duration:** 7 weeks + 1 buffer week
- **Commitment:** 5–8 hours per week
- **Core engineering goals:** DAG execution, distributed workers, a PostgreSQL-based task queue, concurrency safety, failure recovery, cron scheduling, and performance benchmarking

## 1. Technology stack

| Component | Technology |
| --- | --- |
| Programming language | Java 21 |
| Backend framework | Spring Boot 3 |
| Database | PostgreSQL 16 |
| Database migrations | Flyway |
| Database access | JdbcTemplate / raw SQL for the queue; JPA optional for CRUD |
| Testing | JUnit 5 + Testcontainers |
| Monitoring | Micrometer + Prometheus |
| Load testing | k6 |
| Containerization | Docker + Docker Compose |
| Build tool | Maven |
| API documentation | Springdoc OpenAPI / Swagger |

**Architectural decision:** PostgreSQL is both the system of record and the task queue. Kafka, RabbitMQ, and Redis are out of scope for the initial implementation.

## 2. Architecture

```
Client
  └─ REST
API / Scheduler node
  ├─ Workflow API
  ├─ DAG validator
  ├─ Cron ticker
  └─ Stalled-task reaper
PostgreSQL 16
  ├─ Workflow state
  ├─ Task queue  (FOR UPDATE SKIP LOCKED)
  └─ Execution history
Workers (1..N)
  └─ Claim / Execute / Heartbeat
Metrics
  └─ Micrometer · Prometheus · PostgreSQL
```

One codebase, two runtime roles:

- **API / Scheduler** manages workflows, triggers runs, schedules cron executions, and recovers stalled tasks.
- **Worker** claims jobs from PostgreSQL, executes them, sends heartbeats, and updates execution status.

## 3. Database schema

See [database-design.md](database-design.md) for the finalized tables, constraints, and the SQL the queue depends on.

| Table | Purpose |
| --- | --- |
| `workflows` | Logical workflow identity and mutable operational settings |
| `workflow_versions` | Immutable definition versions |
| `tasks` | Task definitions belonging to one version |
| `task_edges` | DAG dependencies for one version |
| `workflow_runs` | One execution of a pinned version |
| `task_runs` | Queue and execution state for a task in a run |
| `task_attempts` | History of each claim |
| `worker_instances` | Worker registration and liveness |

### Task lifecycle

`PENDING` → `READY` → `RUNNING` → `SUCCESS`

From `RUNNING` a task may also become `RETRYING`, `FAILED`, `CANCELLED`, or `SKIPPED`.

| Status | Meaning |
| --- | --- |
| `PENDING` | Dependencies have not completed |
| `READY` | Eligible to be claimed |
| `RUNNING` | Claimed by a worker |
| `RETRYING` | A failed attempt is waiting for its backoff |
| `SUCCESS` | Execution succeeded |
| `FAILED` | Retries are exhausted |
| `CANCELLED` | Execution was cancelled |
| `SKIPPED` | A dependency failure or workflow policy prevented execution |

## 4. Week-by-week plan

Week 1 is expanded because it is the active milestone. Detailed checklists for later weeks are written at the start of that week so unfinished scope is not frozen early. Task counts below are the original estimates.

### Week 1 — Foundation + DAG validation

5–8 hours. Definition of done: invalid DAGs are rejected with useful errors, and valid workflow definitions are persisted.

- [ ] Finalize database schema and relationships
- [ ] Create the GitHub repository `flowforge`
- [ ] Initialize a Spring Boot 3 + Java 21 Maven project
- [ ] Configure PostgreSQL 16 with Docker Compose
- [ ] Configure Flyway
- [ ] Apply the initial schema and constraints
- [ ] Implement `POST /api/v1/workflows`
- [ ] Implement `DagValidator` using Kahn's algorithm
- [ ] Detect cycles, self-loops, duplicate edges, and missing nodes
- [ ] Unit-test single-node, linear, diamond, disconnected, and cyclic DAGs
- [ ] Return meaningful 400 responses for invalid DAGs

### Later weeks

| Week | Focus | Estimate | Planned tasks |
| --- | --- | --- | --- |
| 2 | Workflow runs + a single worker | 5–8 hours | 10 |
| 3 | Dependencies + multiple workers | 5–8 hours | 10 |
| 4 | Reliability and fault tolerance | 5–8 hours | 12 |
| 5 | Cron scheduling and idempotency | 5–8 hours | 8 |
| 6 | Operability and APIs | 5–8 hours | 11 |
| 7 | Benchmarking, hardening, and documentation | 5–8 hours | 14 |
| 8 | Buffer and final polish | 5–8 hours | 11 |

## 5. Failure scenarios that must be demonstrated

Expected behavior is documented in `docs/failure-scenarios.md` when those tests exist. The delivery guarantee is **at-least-once**, not exactly-once.

| Scenario | Expected behavior |
| --- | --- |
| Worker crashes mid-task | Reaper recovers the task after lease expiry |
| Two workers claim the same task | Only one obtains the claim |
| Scheduler instances overlap | Unique constraint prevents duplicate runs |
| PostgreSQL restarts | Workers reconnect; persistent state survives |
| Old worker finishes late | Fencing check rejects the stale update |
| Task fails repeatedly | Retries stop at the configured maximum |
| Dependency permanently fails | Downstream tasks become `SKIPPED` |
| Workflow is cancelled | Pending work is cancelled; running work handles cooperative cancellation |
| Worker pauses during GC | Lease expiry and fencing prevent stale ownership from being trusted |
| Task performs a duplicate side effect | Idempotency mechanism protects the operation where supported |

## 6. What to cut if we fall behind

Protect the engineering fundamentals.

| Cut order | Feature |
| --- | --- |
| 1 | `LISTEN/NOTIFY` polling optimization |
| 2 | Shell task executor |
| 3 | Advanced cron misfire catch-up |
| 4 | Grafana dashboard (keep Prometheus metrics) |
| 5 | Partial DAG rerun (keep full-run retry) |

Never cut:

- PostgreSQL `SKIP LOCKED` claiming
- Independent worker processes
- Heartbeats, the reaper, and fencing
- The multi-worker concurrency test
- The failure-recovery demonstration
- Actual benchmarking
- The README and failure-mode documentation

## 7. Repository structure

```
flowforge/
├── src/main/java/com/flowforge/
│   ├── api/
│   ├── dag/
│   ├── domain/
│   ├── repository/
│   ├── scheduler/
│   ├── worker/
│   ├── executor/
│   └── metrics/
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/
├── src/test/java/
│   ├── dag/
│   ├── worker/
│   ├── concurrency/
│   └── integration/
├── docs/
│   ├── ROADMAP.md
│   ├── database-design.md
│   ├── architecture.md
│   └── failure-scenarios.md
├── load-tests/k6/
├── docker-compose.yml
├── Dockerfile
├── pom.xml
└── README.md
```

Use one codebase. Separate the API and worker by application entry point or Maven module; decide the packaging when the project is initialized.

## 8. Resume targets

These are targets to substantiate with measured results. They are not claims to publish before the tests exist.

**FlowForge | Distributed DAG Workflow Orchestration Engine**

*Java 21, Spring Boot 3, PostgreSQL, Docker, Testcontainers, Prometheus*

- Built a distributed DAG-based workflow orchestration engine supporting dependency-aware scheduling and parallel task execution across multiple worker instances.
- Implemented PostgreSQL-based concurrent job claiming using `FOR UPDATE SKIP LOCKED`, with measured throughput and zero duplicate concurrent claims under a documented stress test.
- Engineered fault-tolerant execution with heartbeat-based failure detection, fencing tokens, exponential-backoff retries, and recovery of abandoned tasks after worker crashes.

## 9. Working rules

1. One milestone at a time.
2. Understand the concept before writing the implementation.
3. Write the code in Java 21.
4. Explain important SQL and transaction boundaries.
5. Test every major feature before marking it complete.
6. Commit after a working milestone.
7. Document architectural decisions and trade-offs.
8. Never fabricate benchmark numbers or untested guarantees.
