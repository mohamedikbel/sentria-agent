CREATE TABLE behavior_session (
                                  id TEXT PRIMARY KEY,
                                  device_id TEXT NOT NULL,
                                  session_type TEXT NOT NULL,
                                  started_at TEXT NOT NULL,
                                  ended_at TEXT,
                                  context TEXT
);