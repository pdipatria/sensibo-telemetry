package com.pdipatria.sensibo;

import com.pdipatria.sensibo.model.HistoricalMeasurements;
import com.pdipatria.sensibo.model.Measurement;
import com.pdipatria.sensibo.model.SensiboResponse;
import com.pdipatria.sensibo.service.MeasurementIngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MeasurementIngestionServiceTest extends PostgresIntegrationTest {
    private static final Instant TIME = Instant.parse("2026-10-30T00:00:13Z");
    private static final Measurement TEMPERATURE = new Measurement(TIME, 18.1);
    private static final Measurement HUMIDITY = new Measurement(TIME, 62.5);

    @Autowired MeasurementIngestionService ingestion;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.pdipatria.sensibo.service.ExecutionHistory history;

    @BeforeEach
    void clearMeasurements() {
        jdbc.update("DELETE FROM measurement");
        jdbc.update("DELETE FROM import_execution");
    }

    @Test
    void importsBothTypesWithConfiguredDeviceAndUtcTimestamp() {
        ingest(response(List.of(TEMPERATURE), List.of(HUMIDITY)));

        assertEquals(2, count());
        assertMeasurement("TEMPERATURE", TIME, 18.1);
        assertMeasurement("HUMIDITY", TIME, 62.5);
    }

    @Test
    void repeatedAndOverlappingImportsKeepExistingValuesAndInsertNewMeasurements() {
        var initial = response(List.of(TEMPERATURE), List.of(HUMIDITY));
        ingest(initial);
        ingest(initial);
        assertEquals(2, count());

        var later = new Measurement(TIME.plusSeconds(90), 19.2);
        ingest(response(List.of(new Measurement(TIME, 99), later), List.of(HUMIDITY)));

        assertEquals(3, count());
        assertMeasurement("TEMPERATURE", TIME, 18.1);
        assertMeasurement("TEMPERATURE", later.time(), later.value());
    }

    @Test
    void failedImportRollsBackTemperatureWhenHumidityIsInvalid() {
        var invalid = new Measurement(null, 60);
        assertThrows(IllegalArgumentException.class,
                () -> ingest(response(List.of(TEMPERATURE), List.of(invalid))));
        assertEquals(0, count());
    }

    @Test
    void sameTimestampAndTypeForDifferentDevicesAreDistinct() {
        ingest(response(List.of(TEMPERATURE), List.of()));
        ingestion.ingest("another-device", history.begin("another-device"), response(List.of(TEMPERATURE), List.of()));
        assertEquals(2, count());
    }

    @Test
    void emptyImportIsValidAndErrorResponseIsRejected() {
        ingest(response(List.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> ingest(new SensiboResponse("error", null)));
        assertEquals(0, count());
    }

    private void ingest(SensiboResponse response) {
        ingestion.ingest("test-device", history.begin("test-device"), response);
    }

    private int count() {
        return jdbc.queryForObject("SELECT count(*) FROM measurement", Integer.class);
    }

    private void assertMeasurement(String type, Instant time, double value) {
        jdbc.queryForObject("""
                SELECT measured_at, value FROM measurement
                WHERE device_id = ? AND measurement_type = ? AND measured_at = ?
                """, (rs, row) -> {
            assertEquals(time, rs.getObject("measured_at", OffsetDateTime.class).toInstant());
            assertEquals(value, rs.getDouble("value"), 0.000001);
            return true;
        }, "test-device", type, time.atOffset(java.time.ZoneOffset.UTC));
    }

    private SensiboResponse response(List<Measurement> temperature, List<Measurement> humidity) {
        return new SensiboResponse("success", new HistoricalMeasurements(temperature, humidity));
    }
}
