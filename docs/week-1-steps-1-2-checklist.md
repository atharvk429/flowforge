# Week 1 — Steps 1 and 2 verification

Verified on 18 November 2026 against PostgreSQL 16.16 in Docker and Spring Boot 3.5.0. A migration is complete only because Flyway applied it successfully; it is not inferred from the presence of SQL files.

## Step 1 — Database design (13/13)

- [x] Versioned workflow definitions and published-version immutability are documented and implemented.
- [x] Definition tasks are separated from execution task runs.
- [x] Definition edges are separated from the `run_task_edges` execution snapshot.
- [x] `pending_deps` is defined for dependency resolution.
- [x] The later `SKIP LOCKED` claim approach is documented.
- [x] `worker_instances` provides worker identity and heartbeat tracking.
- [x] `task_attempts` records execution history.
- [x] `locked_by` plus monotonically increasing `attempt` is the fencing contract.
- [x] `V1__initial_schema.sql` exists in Flyway's migration directory.
- [x] Docker Compose provisions PostgreSQL 16.
- [x] The deferred current-version composite foreign key is present.
- [x] Flyway applied V1 successfully.
- [x] All nine FlowForge tables, indexes, constraints, and triggers were verified (plus Flyway's history table).

## Step 2 — Initialize Spring Boot (19/19)

- [x] Spring Boot 3 Maven project targets Java 21 (`--release 21`).
- [x] Execute the checks on Temurin JDK 21.0.13 (LTS); `mvn clean verify` and `mvn clean install` succeeded.
- [x] Open the project in an IDE (verified from the active IDE session).
- [x] Required Maven dependencies are present.
- [x] Flyway's PostgreSQL database module is present.
- [x] Micrometer's Prometheus registry is present.
- [x] `docker-compose.yml` exists.
- [x] PostgreSQL 16 container is running and accepted a `psql` connection.
- [x] `application.yml` configures datasource, Flyway, JPA, server port, and Actuator exposure.
- [x] V1 is on the classpath migration path.
- [x] `FlowForgeApplication` is present under `com.flowforge`.
- [x] `mvn clean verify` and `mvn clean install` succeeded.
- [x] Spring Boot started successfully in an isolated container on the Compose network.
- [x] Flyway validated and applied V1.
- [x] All nine FlowForge tables were found.
- [x] `flyway_schema_history` records version 1.
- [x] The migrated database contains 28 indexes, 60 constraints, and 12 triggers.
- [x] `/actuator/health` returned `{"status":"UP"}`.
- [x] `/actuator/prometheus` returned HTTP 200.

## Local-machine note

The host has higher-priority datasource environment settings, so a host-launched Spring process did not target the Compose database. This project remains correctly configurable through `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`; the isolated container supplied the equivalent `SPRING_DATASOURCE_*` values and completed the runtime verification without changing machine-level settings.
