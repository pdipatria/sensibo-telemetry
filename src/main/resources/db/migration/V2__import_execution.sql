CREATE TABLE import_execution (
    device_id TEXT NOT NULL,
    import_seq_num INTEGER NOT NULL CHECK (import_seq_num > 0),
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    status TEXT NOT NULL CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED')),
    failure_reason TEXT,
    PRIMARY KEY (device_id, import_seq_num)
);
ALTER TABLE measurement ADD COLUMN import_seq_num INTEGER;
ALTER TABLE measurement ADD CONSTRAINT measurement_import_execution_fk
    FOREIGN KEY (device_id, import_seq_num) REFERENCES import_execution(device_id, import_seq_num);
