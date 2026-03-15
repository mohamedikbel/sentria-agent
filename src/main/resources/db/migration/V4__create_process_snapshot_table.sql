CREATE TABLE process_snapshot (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id TEXT NOT NULL,
    process_name TEXT NOT NULL,
    pid INTEGER NOT NULL,
    cpu_percent REAL NOT NULL,
    memory_mb REAL NOT NULL,
    command_line TEXT,
    captured_at TEXT NOT NULL
);

