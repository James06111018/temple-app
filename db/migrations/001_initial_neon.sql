BEGIN;

CREATE TABLE IF NOT EXISTS light_members (
    id BIGSERIAL PRIMARY KEY,
    name TEXT,
    phone TEXT,
    city TEXT,
    dist TEXT,
    address TEXT,
    zip_code TEXT,
    birth_date TEXT,
    lunar_birth_date TEXT,
    age INTEGER,
    zodiac TEXT,
    zodiac_year TEXT,
    birth_time TEXT,
    note TEXT,
    contact_person TEXT,
    id_number TEXT,
    sort_order INTEGER,
    ding INTEGER,
    kou INTEGER,
    is_mail TEXT,
    gender TEXT,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    uuid TEXT UNIQUE,
    updated_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    version INTEGER NOT NULL DEFAULT 1,
    device_id TEXT,
    sync_status TEXT NOT NULL DEFAULT 'clean'
);

CREATE TABLE IF NOT EXISTS donations (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL,
    receipt_no TEXT,
    donate_date TEXT,
    extra_no TEXT,
    amount INTEGER,
    summary TEXT,
    donate_note TEXT,
    other_note TEXT,
    donor_no TEXT,
    light_no TEXT,
    should_pay INTEGER,
    donate_type TEXT,
    creator TEXT,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    uuid TEXT UNIQUE,
    updated_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    version INTEGER NOT NULL DEFAULT 1,
    device_id TEXT,
    sync_status TEXT NOT NULL DEFAULT 'clean',
    CONSTRAINT fk_donations_member
        FOREIGN KEY (member_id)
        REFERENCES light_members(id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_light_members_uuid ON light_members(uuid);
CREATE INDEX IF NOT EXISTS idx_donations_uuid ON donations(uuid);
CREATE INDEX IF NOT EXISTS idx_donations_member_id ON donations(member_id);
CREATE INDEX IF NOT EXISTS idx_light_members_sync_status ON light_members(sync_status);
CREATE INDEX IF NOT EXISTS idx_donations_sync_status ON donations(sync_status);

CREATE TABLE IF NOT EXISTS sync_state (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    last_pull_at TIMESTAMPTZ,
    last_push_at TIMESTAMPTZ,
    last_sync_at TIMESTAMPTZ,
    last_sync_token TEXT,
    device_id TEXT NOT NULL,
    conflict_policy TEXT NOT NULL DEFAULT 'LAST_WRITE_WINS',
    remote_base_url TEXT,
    updated_at TIMESTAMPTZ
);

INSERT INTO sync_state (id, device_id, conflict_policy, updated_at)
VALUES (1, 'local', 'LAST_WRITE_WINS', NOW())
ON CONFLICT (id) DO NOTHING;

COMMIT;
