CREATE TABLE finding (
                         id TEXT PRIMARY KEY,
                         type TEXT NOT NULL,
                         severity TEXT NOT NULL,
                         confidence TEXT NOT NULL,
                         facts TEXT NOT NULL,
                         likely_contributor TEXT,
                         recommendations TEXT NOT NULL,
                         created_at TEXT NOT NULL
);