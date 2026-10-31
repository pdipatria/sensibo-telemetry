package com.pdipatria.sensibo.service;

import com.pdipatria.sensibo.model.Measurement;
import com.pdipatria.sensibo.model.SensiboResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.time.ZoneOffset;
import java.util.List;

@Service
public class MeasurementIngestionService {
    private static final String INSERT = """
        INSERT INTO measurement (device_id, measurement_type, measured_at, value, import_seq_num)
        VALUES (?, ?, ?, ?, ?)
        ON CONFLICT (device_id, measurement_type, measured_at) DO UPDATE SET import_seq_num = EXCLUDED.import_seq_num
        """;

    private final JdbcTemplate jdbc;
    public MeasurementIngestionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void ingest(String deviceId, int seq, SensiboResponse response) {
        Assert.notNull(response, "Response must not be null");
        Assert.isTrue("success".equals(response.status()), "Response must have success status");
        Assert.notNull(response.result(), "Response result must not be null");
        persist(deviceId, seq, "TEMPERATURE", response.result().temperature());
        persist(deviceId, seq, "HUMIDITY", response.result().humidity());
        jdbc.update("UPDATE import_execution SET status = 'SUCCEEDED', completed_at = CURRENT_TIMESTAMP WHERE device_id = ? AND import_seq_num = ?", deviceId, seq);
    }

    private void persist(String deviceId, int seq, String type, List<Measurement> measurements) {
        Assert.notNull(measurements, type + " measurements must not be null");
        jdbc.batchUpdate(INSERT, measurements, 500, (statement, measurement) -> {
            Assert.notNull(measurement, "Measurement must not be null");
            Assert.notNull(measurement.time(), "Measurement time must not be null");
            Assert.isTrue(Double.isFinite(measurement.value()), "Measurement value must be finite");
            statement.setString(1, deviceId);
            statement.setString(2, type);
            statement.setObject(3, measurement.time().atOffset(ZoneOffset.UTC));
            statement.setDouble(4, measurement.value());
            statement.setInt(5, seq);
        });
    }
}
