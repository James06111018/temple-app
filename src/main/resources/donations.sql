CREATE TABLE IF NOT EXISTS donations (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    member_id INTEGER NOT NULL,
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
    FOREIGN KEY(member_id) REFERENCES light_members(id) ON DELETE CASCADE
);
