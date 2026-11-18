-- FlowForge initial schema. PostgreSQL 16.
-- Draft definitions become immutable when published; each run has a frozen graph.

CREATE TABLE workflows (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(150) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    current_version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_workflows_name UNIQUE (name),
    CONSTRAINT ck_workflows_name CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_workflows_current_version CHECK (current_version > 0)
);

CREATE TABLE workflow_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id UUID NOT NULL REFERENCES workflows (id) ON DELETE CASCADE,
    version INTEGER NOT NULL,
    cron_expr VARCHAR(100),
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_workflow_version UNIQUE (workflow_id, version),
    CONSTRAINT uq_workflow_version_id UNIQUE (workflow_id, id),
    CONSTRAINT ck_version_positive CHECK (version > 0)
);

-- The first version and its workflow are inserted in one transaction.
ALTER TABLE workflows ADD CONSTRAINT fk_workflow_current_version
    FOREIGN KEY (id, current_version) REFERENCES workflow_versions (workflow_id, version)
    DEFERRABLE INITIALLY DEFERRED;

CREATE TABLE workflow_tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_version_id UUID NOT NULL REFERENCES workflow_versions (id) ON DELETE CASCADE,
    task_key VARCHAR(100) NOT NULL,
    task_name VARCHAR(150) NOT NULL,
    task_type VARCHAR(30) NOT NULL,
    config JSONB NOT NULL DEFAULT '{}'::jsonb,
    max_retries INTEGER NOT NULL DEFAULT 3,
    backoff_ms BIGINT NOT NULL DEFAULT 1000,
    timeout_seconds INTEGER NOT NULL DEFAULT 60,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_version_task_key UNIQUE (workflow_version_id, task_key),
    CONSTRAINT uq_version_task_id UNIQUE (workflow_version_id, id),
    CONSTRAINT ck_task_key CHECK (length(btrim(task_key)) > 0),
    CONSTRAINT ck_task_name CHECK (length(btrim(task_name)) > 0),
    CONSTRAINT ck_task_type CHECK (task_type IN ('HTTP', 'DELAY', 'LOG', 'FAIL_N_TIMES')),
    CONSTRAINT ck_task_config CHECK (jsonb_typeof(config) = 'object'),
    CONSTRAINT ck_max_retries CHECK (max_retries >= 0),
    CONSTRAINT ck_backoff CHECK (backoff_ms >= 0),
    CONSTRAINT ck_timeout CHECK (timeout_seconds > 0)
);

CREATE TABLE task_edges (
    workflow_version_id UUID NOT NULL,
    from_task_id UUID NOT NULL,
    to_task_id UUID NOT NULL,
    CONSTRAINT pk_task_edges PRIMARY KEY (workflow_version_id, from_task_id, to_task_id),
    CONSTRAINT fk_edge_source FOREIGN KEY (workflow_version_id, from_task_id)
        REFERENCES workflow_tasks (workflow_version_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_edge_destination FOREIGN KEY (workflow_version_id, to_task_id)
        REFERENCES workflow_tasks (workflow_version_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_no_self_loop CHECK (from_task_id <> to_task_id)
);
CREATE INDEX idx_task_edges_destination ON task_edges (workflow_version_id, to_task_id);

CREATE TABLE worker_instances (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hostname VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_heartbeat_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    stopped_at TIMESTAMPTZ,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT ck_worker_hostname CHECK (length(btrim(hostname)) > 0),
    CONSTRAINT ck_worker_status CHECK (status IN ('ACTIVE', 'DRAINING', 'STOPPED')),
    CONSTRAINT ck_worker_metadata CHECK (jsonb_typeof(metadata) = 'object')
);
CREATE INDEX idx_worker_heartbeat ON worker_instances (last_heartbeat_at) WHERE status = 'ACTIVE';

CREATE TABLE workflow_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id UUID NOT NULL REFERENCES workflows (id),
    workflow_version_id UUID NOT NULL,
    workflow_name_snapshot VARCHAR(150) NOT NULL,
    workflow_version_snapshot INTEGER NOT NULL,
    cron_expr_snapshot VARCHAR(100),
    trigger_type VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    scheduled_for TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    CONSTRAINT fk_run_version FOREIGN KEY (workflow_id, workflow_version_id)
        REFERENCES workflow_versions (workflow_id, id),
    CONSTRAINT ck_workflow_version_snapshot CHECK (workflow_version_snapshot > 0),
    CONSTRAINT ck_trigger_type CHECK (trigger_type IN ('MANUAL', 'CRON')),
    CONSTRAINT ck_run_status CHECK (status IN ('CREATED', 'RUNNING', 'SUCCESS', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_scheduled_trigger CHECK ((trigger_type = 'CRON' AND scheduled_for IS NOT NULL) OR (trigger_type = 'MANUAL' AND scheduled_for IS NULL))
);
CREATE UNIQUE INDEX uq_workflow_scheduled_run ON workflow_runs (workflow_id, scheduled_for) WHERE scheduled_for IS NOT NULL;
CREATE INDEX idx_workflow_runs_history ON workflow_runs (workflow_id, created_at DESC);

CREATE TABLE task_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_run_id UUID NOT NULL REFERENCES workflow_runs (id) ON DELETE CASCADE,
    task_key VARCHAR(100) NOT NULL,
    task_name VARCHAR(150) NOT NULL,
    task_type VARCHAR(30) NOT NULL,
    config JSONB NOT NULL DEFAULT '{}'::jsonb,
    max_retries INTEGER NOT NULL,
    backoff_ms BIGINT NOT NULL,
    timeout_seconds INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt INTEGER NOT NULL DEFAULT 0,
    pending_deps INTEGER NOT NULL DEFAULT 0,
    run_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    locked_by UUID REFERENCES worker_instances (id) ON DELETE SET NULL,
    heartbeat_at TIMESTAMPTZ,
    lease_expires_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    error TEXT,
    output JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_run_task_key UNIQUE (workflow_run_id, task_key),
    CONSTRAINT uq_run_task_id UNIQUE (workflow_run_id, id),
    CONSTRAINT ck_task_run_status CHECK (status IN ('PENDING', 'READY', 'RUNNING', 'RETRYING', 'SUCCESS', 'FAILED', 'CANCELLED', 'SKIPPED')),
    CONSTRAINT ck_task_run_attempt CHECK (attempt >= 0),
    CONSTRAINT ck_pending_deps CHECK (pending_deps >= 0),
    CONSTRAINT ck_task_run_retries CHECK (max_retries >= 0),
    CONSTRAINT ck_task_run_backoff CHECK (backoff_ms >= 0),
    CONSTRAINT ck_task_run_timeout CHECK (timeout_seconds > 0),
    CONSTRAINT ck_task_run_output CHECK (output IS NULL OR jsonb_typeof(output) = 'object'),
    CONSTRAINT ck_running_has_worker CHECK (status <> 'RUNNING' OR (locked_by IS NOT NULL AND heartbeat_at IS NOT NULL AND lease_expires_at IS NOT NULL))
);
CREATE INDEX idx_task_runs_claim ON task_runs (run_at, created_at) WHERE status IN ('READY', 'RETRYING');
CREATE INDEX idx_task_runs_worker ON task_runs (locked_by) WHERE status = 'RUNNING';
CREATE INDEX idx_task_runs_workflow_run ON task_runs (workflow_run_id);

CREATE TABLE run_task_edges (
    workflow_run_id UUID NOT NULL,
    from_task_run_id UUID NOT NULL,
    to_task_run_id UUID NOT NULL,
    CONSTRAINT pk_run_task_edges PRIMARY KEY (workflow_run_id, from_task_run_id, to_task_run_id),
    CONSTRAINT fk_run_edge_source FOREIGN KEY (workflow_run_id, from_task_run_id)
        REFERENCES task_runs (workflow_run_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_run_edge_destination FOREIGN KEY (workflow_run_id, to_task_run_id)
        REFERENCES task_runs (workflow_run_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_run_no_self_loop CHECK (from_task_run_id <> to_task_run_id)
);
CREATE INDEX idx_run_edges_destination ON run_task_edges (workflow_run_id, to_task_run_id);

CREATE TABLE task_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_run_id UUID NOT NULL REFERENCES task_runs (id) ON DELETE CASCADE,
    attempt_number INTEGER NOT NULL,
    worker_instance_id UUID REFERENCES worker_instances (id) ON DELETE SET NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at TIMESTAMPTZ,
    error TEXT,
    CONSTRAINT uq_task_attempt_number UNIQUE (task_run_id, attempt_number),
    CONSTRAINT ck_attempt_number CHECK (attempt_number > 0),
    CONSTRAINT ck_attempt_status CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED', 'ABANDONED', 'CANCELLED'))
);
CREATE INDEX idx_task_attempts_history ON task_attempts (task_run_id, attempt_number DESC);

CREATE FUNCTION set_updated_at() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN NEW.updated_at = now(); RETURN NEW; END;
$$;
CREATE TRIGGER trg_workflow_updated_at BEFORE UPDATE ON workflows FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_task_run_updated_at BEFORE UPDATE ON task_runs FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE FUNCTION prevent_published_version_changes() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.published_at IS NOT NULL THEN
        RAISE EXCEPTION 'Published workflow versions cannot be modified or deleted' USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END;
$$;
CREATE TRIGGER trg_immutable_workflow_version BEFORE UPDATE OR DELETE ON workflow_versions FOR EACH ROW EXECUTE FUNCTION prevent_published_version_changes();

CREATE FUNCTION prevent_published_definition_changes() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE version_id UUID; version_published_at TIMESTAMPTZ;
BEGIN
    IF TG_OP = 'UPDATE' AND OLD.workflow_version_id IS DISTINCT FROM NEW.workflow_version_id THEN
        RAISE EXCEPTION 'Moving a task or edge between versions is not allowed';
    END IF;
    version_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.workflow_version_id ELSE NEW.workflow_version_id END;
    SELECT published_at INTO version_published_at FROM workflow_versions WHERE id = version_id;
    IF version_published_at IS NOT NULL THEN
        RAISE EXCEPTION 'Tasks and edges of a published version are immutable' USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END;
$$;
CREATE TRIGGER trg_immutable_workflow_tasks BEFORE INSERT OR UPDATE OR DELETE ON workflow_tasks FOR EACH ROW EXECUTE FUNCTION prevent_published_definition_changes();
CREATE TRIGGER trg_immutable_task_edges BEFORE INSERT OR UPDATE OR DELETE ON task_edges FOR EACH ROW EXECUTE FUNCTION prevent_published_definition_changes();

CREATE FUNCTION ensure_published_version_for_run() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE version_published_at TIMESTAMPTZ;
BEGIN
    SELECT published_at INTO version_published_at FROM workflow_versions
    WHERE id = NEW.workflow_version_id AND workflow_id = NEW.workflow_id;
    IF version_published_at IS NULL THEN
        RAISE EXCEPTION 'Cannot execute an unpublished workflow version' USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_run_requires_published_version BEFORE INSERT OR UPDATE OF workflow_id, workflow_version_id ON workflow_runs FOR EACH ROW EXECUTE FUNCTION ensure_published_version_for_run();
