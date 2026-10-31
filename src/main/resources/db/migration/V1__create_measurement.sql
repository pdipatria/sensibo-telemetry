CREATE TABLE measurement (
    device_id TEXT NOT NULL,
    measurement_type TEXT NOT NULL
        CHECK (measurement_type IN ('TEMPERATURE', 'HUMIDITY')),
    measured_at TIMESTAMP WITH TIME ZONE NOT NULL,
    value DOUBLE PRECISION NOT NULL,
    CONSTRAINT measurement_device_type_time_unique
        UNIQUE (device_id, measurement_type, measured_at)
);
