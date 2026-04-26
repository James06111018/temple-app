CREATE TABLE donation_category (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    code TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    type TEXT,
    amount INTEGER DEFAULT 0,
    is_enabled INTEGER DEFAULT 1,   -- SQLite 沒有 boolean，用 0/1
    sort INTEGER DEFAULT 0,
    remark TEXT,
    is_system INTEGER DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);