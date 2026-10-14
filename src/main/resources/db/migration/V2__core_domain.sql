-- Core volunteering domain: events, attendance, hours, tasks, incidents, messaging, notifications

ALTER TABLE users ADD COLUMN google_sub VARCHAR(255) UNIQUE;

CREATE TABLE events (
    id              UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    category        VARCHAR(64),
    venue           VARCHAR(200),
    address         VARCHAR(500),
    is_remote       BOOLEAN NOT NULL DEFAULT FALSE,
    meeting_link    VARCHAR(500),
    event_date      DATE NOT NULL,
    start_time      TIME NOT NULL,
    end_time        TIME NOT NULL,
    capacity        INT NOT NULL,
    status          VARCHAR(32) NOT NULL,
    image_url       VARCHAR(500),
    check_in_code   VARCHAR(16) NOT NULL,
    created_by      UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT events_capacity_check CHECK (capacity > 0),
    CONSTRAINT events_time_check CHECK (end_time > start_time),
    CONSTRAINT events_status_check CHECK (status IN ('DRAFT', 'OPEN', 'CLOSED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'))
);

CREATE INDEX idx_events_org_date ON events(organization_id, event_date);

CREATE TABLE event_registrations (
    id              UUID PRIMARY KEY,
    event_id        UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status          VARCHAR(32) NOT NULL,
    checked_in_at   TIMESTAMPTZ,
    checked_out_at  TIMESTAMPTZ,
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT event_registrations_unique UNIQUE (event_id, user_id),
    CONSTRAINT event_registrations_status_check CHECK (status IN ('CONFIRMED', 'WAITLIST', 'CANCELLED', 'CHECKED_IN', 'COMPLETED', 'NO_SHOW'))
);

CREATE INDEX idx_event_registrations_user ON event_registrations(user_id);

CREATE TABLE hour_logs (
    id              UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    event_id        UUID REFERENCES events(id) ON DELETE SET NULL,
    registration_id UUID UNIQUE REFERENCES event_registrations(id) ON DELETE SET NULL,
    work_date       DATE NOT NULL,
    hours           NUMERIC(6, 2) NOT NULL,
    description     TEXT,
    status          VARCHAR(16) NOT NULL,
    source          VARCHAR(16) NOT NULL,
    reviewed_by     UUID REFERENCES users(id) ON DELETE SET NULL,
    reviewed_at     TIMESTAMPTZ,
    review_note     TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT hour_logs_hours_check CHECK (hours > 0 AND hours <= 24),
    CONSTRAINT hour_logs_status_check CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT hour_logs_source_check CHECK (source IN ('ATTENDANCE', 'MANUAL'))
);

CREATE INDEX idx_hour_logs_user ON hour_logs(user_id, status);
CREATE INDEX idx_hour_logs_org_status ON hour_logs(organization_id, status);

CREATE TABLE tasks (
    id              UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    event_id        UUID REFERENCES events(id) ON DELETE SET NULL,
    title           VARCHAR(200) NOT NULL,
    description     TEXT,
    priority        VARCHAR(16) NOT NULL,
    status          VARCHAR(16) NOT NULL,
    assignee_id     UUID REFERENCES users(id) ON DELETE SET NULL,
    due_date        DATE,
    created_by      UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT tasks_priority_check CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    CONSTRAINT tasks_status_check CHECK (status IN ('TODO', 'IN_PROGRESS', 'DONE', 'CANCELLED'))
);

CREATE INDEX idx_tasks_org ON tasks(organization_id, status);
CREATE INDEX idx_tasks_assignee ON tasks(assignee_id);

CREATE TABLE incidents (
    id              UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    event_id        UUID REFERENCES events(id) ON DELETE SET NULL,
    reported_by     UUID REFERENCES users(id) ON DELETE SET NULL,
    title           VARCHAR(200) NOT NULL,
    description     TEXT NOT NULL,
    severity        VARCHAR(16) NOT NULL,
    status          VARCHAR(16) NOT NULL,
    resolution      TEXT,
    resolved_by     UUID REFERENCES users(id) ON DELETE SET NULL,
    resolved_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT incidents_severity_check CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT incidents_status_check CHECK (status IN ('OPEN', 'RESOLVED'))
);

CREATE INDEX idx_incidents_org ON incidents(organization_id, status);

CREATE TABLE messages (
    id              UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    channel         VARCHAR(32) NOT NULL,
    sender_id       UUID REFERENCES users(id) ON DELETE SET NULL,
    content         TEXT NOT NULL,
    urgent          BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT messages_channel_check CHECK (channel IN ('GENERAL', 'LEADERS', 'ANNOUNCEMENTS'))
);

CREATE INDEX idx_messages_org_channel ON messages(organization_id, channel, created_at);

CREATE TABLE notifications (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type            VARCHAR(32) NOT NULL,
    title           VARCHAR(200) NOT NULL,
    body            TEXT,
    link            VARCHAR(300),
    read_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_user ON notifications(user_id, created_at);

CREATE TABLE password_reset_tokens (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash      VARCHAR(64) NOT NULL UNIQUE,
    expires_at      TIMESTAMPTZ NOT NULL,
    used_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
