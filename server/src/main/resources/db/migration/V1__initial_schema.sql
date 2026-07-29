-- ============================================================
-- Task Hub — Migración Inicial (V1)
-- Crea el esquema base de la base de datos
-- ============================================================

-- Hogar / unidad de convivencia
CREATE TABLE IF NOT EXISTS household (
    id          TEXT PRIMARY KEY,
    name        TEXT NOT NULL,
    invite_code TEXT NOT NULL UNIQUE,
    created_at  BIGINT NOT NULL,
    updated_at  BIGINT NOT NULL
);

-- Miembro del hogar
CREATE TABLE IF NOT EXISTS member (
    id           TEXT PRIMARY KEY,
    household_id TEXT NOT NULL REFERENCES household(id) ON DELETE CASCADE,
    display_name TEXT NOT NULL,
    avatar_url   TEXT,
    role         TEXT NOT NULL DEFAULT 'child',
    total_points INTEGER NOT NULL DEFAULT 0,
    joined_at    BIGINT NOT NULL,
    left_at      BIGINT
);

-- Tarea (plantilla)
CREATE TABLE IF NOT EXISTS task (
    id              TEXT PRIMARY KEY,
    household_id    TEXT NOT NULL REFERENCES household(id) ON DELETE CASCADE,
    created_by      TEXT NOT NULL REFERENCES member(id),
    title           TEXT NOT NULL,
    description     TEXT,
    points          INTEGER NOT NULL DEFAULT 10,
    frequency       TEXT NOT NULL DEFAULT 'once',
    recurrence_days JSONB,
    tags            TEXT[],
    penalty_mode     TEXT,
    penalty_value    INTEGER,
    penalty_interval TEXT,
    penalty_max      INTEGER,
    created_at      BIGINT NOT NULL,
    updated_at      BIGINT NOT NULL
);

-- Asignación de tarea a miembro(s)
CREATE TABLE IF NOT EXISTS task_assignment (
    id          TEXT PRIMARY KEY,
    task_id     TEXT NOT NULL REFERENCES task(id) ON DELETE CASCADE,
    member_id   TEXT NOT NULL REFERENCES member(id) ON DELETE CASCADE,
    mandatory   INTEGER NOT NULL DEFAULT 0,
    due_date    BIGINT,
    assigned_at BIGINT NOT NULL,
    UNIQUE(task_id, member_id)
);

-- Registro de tarea completada
CREATE TABLE IF NOT EXISTS task_completion (
    id              TEXT PRIMARY KEY,
    assignment_id   TEXT NOT NULL REFERENCES task_assignment(id) ON DELETE CASCADE,
    completed_at    BIGINT NOT NULL,
    points_awarded  INTEGER NOT NULL,
    on_time         INTEGER NOT NULL
);

-- Penalización por retraso
CREATE TABLE IF NOT EXISTS penalty (
    id              TEXT PRIMARY KEY,
    assignment_id   TEXT NOT NULL REFERENCES task_assignment(id) ON DELETE CASCADE,
    points_deducted INTEGER NOT NULL,
    reason          TEXT NOT NULL,
    applied_at      BIGINT NOT NULL
);

-- Rachas del miembro
CREATE TABLE IF NOT EXISTS streak (
    id           TEXT PRIMARY KEY,
    member_id    TEXT NOT NULL REFERENCES member(id) ON DELETE CASCADE,
    type         TEXT NOT NULL DEFAULT 'daily',
    count        INTEGER NOT NULL DEFAULT 0,
    best         INTEGER NOT NULL DEFAULT 0,
    last_updated BIGINT NOT NULL,
    UNIQUE(member_id, type)
);

-- Logros desbloqueados
CREATE TABLE IF NOT EXISTS achievement (
    id          TEXT PRIMARY KEY,
    member_id   TEXT NOT NULL REFERENCES member(id) ON DELETE CASCADE,
    key         TEXT NOT NULL,
    unlocked_at BIGINT NOT NULL,
    UNIQUE(member_id, key)
);
