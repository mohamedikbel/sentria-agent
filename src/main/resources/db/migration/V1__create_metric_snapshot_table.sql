CREATE TABLE metric_snapshot (
                                 id INTEGER PRIMARY KEY AUTOINCREMENT,
                                 device_id TEXT NOT NULL,
                                 metric_type TEXT NOT NULL,
                                 metric_value REAL NOT NULL,
                                 captured_at TEXT NOT NULL
);