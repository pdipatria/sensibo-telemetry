package com.pdipatria.sensibo;

import com.pdipatria.sensibo.client.SensiboClient;
import com.pdipatria.sensibo.config.DeviceConfiguration;
import com.pdipatria.sensibo.model.*;
import com.pdipatria.sensibo.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class ExecutionHistoryTest extends PostgresIntegrationTest {
    @Autowired ExecutionHistory history;
    @Autowired MeasurementIngestionService ingestion;
    @Autowired JdbcTemplate jdbc;
    SensiboClient client;
    HistoricalImportService importer;
    @BeforeEach void setup() {
        jdbc.update("DELETE FROM measurement");
        jdbc.update("DELETE FROM import_execution");
        client = mock(SensiboClient.class);
        importer = new HistoricalImportService(client, ingestion, history);
    }
    private SensiboResponse response(List<Measurement> temperature, List<Measurement> humidity) {
        return new SensiboResponse("success", new HistoricalMeasurements(temperature, humidity));
    }
    @Test void emptyFailedAndIndependentAttemptsSurviveNewPollingInstances() {
        assertEquals(new ExecutionHistory.Summary(0, 0, null), history.summary("a"));
        when(client.fetchHistoricalMeasurements("a")).thenReturn(response(List.of(), List.of()));
        importer.importHistory("a");
        when(client.fetchHistoricalMeasurements("a")).thenThrow(new IllegalStateException("credential-secret"));
        assertThrows(IllegalStateException.class, () -> importer.importHistory("a"));
        when(client.fetchHistoricalMeasurements("b")).thenReturn(response(List.of(), List.of()));
        importer.importHistory("b");
        assertEquals(new ExecutionHistory.Summary(2, 1, "FAILED"), history.summary("a"));
        assertEquals(new ExecutionHistory.Summary(1, 1, "SUCCEEDED"), history.summary("b"));
        assertFalse(jdbc.queryForObject("SELECT failure_reason FROM import_execution WHERE device_id='a' AND import_seq_num=2", String.class).contains("secret"));
        var polling = new ScheduledPolling(importer, new DeviceConfiguration("a,b", ""), history);
        assertEquals(2, polling.status("a").devices().getFirst().importSeqNum());
        assertTrue(polling.status().devices().stream().allMatch(ScheduledPolling.Status::enabled));
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> polling.status("unknown")).getStatusCode().value());
    }
    @Test void failedIngestionRollsBackInsertsAndConflictSequenceUpdates() {
        var original = new Measurement(Instant.parse("2026-10-30T00:00:00Z"), 18);
        when(client.fetchHistoricalMeasurements("a")).thenReturn(response(List.of(original), List.of()));
        importer.importHistory("a");
        when(client.fetchHistoricalMeasurements("a")).thenReturn(response(List.of(new Measurement(original.time(), 99)), List.of(new Measurement(null, 5))));
        assertThrows(IllegalStateException.class, () -> importer.importHistory("a"));
        assertEquals(1, jdbc.queryForObject("SELECT import_seq_num FROM measurement", Integer.class));
        assertEquals(18, jdbc.queryForObject("SELECT value FROM measurement", Double.class));
        when(client.fetchHistoricalMeasurements("a")).thenReturn(response(List.of(new Measurement(original.time(), 99)), List.of()));
        importer.importHistory("a");
        assertEquals(3, jdbc.queryForObject("SELECT import_seq_num FROM measurement", Integer.class));
        assertEquals(18, jdbc.queryForObject("SELECT value FROM measurement", Double.class));
        assertEquals(new ExecutionHistory.Summary(3, 3, "SUCCEEDED"), history.summary("a"));
    }
    @Test void pollingAndOneShotContinueAfterFailureAndRespectDeviceControls() {
        when(client.fetchHistoricalMeasurements("a")).thenThrow(new IllegalStateException());
        when(client.fetchHistoricalMeasurements("b")).thenReturn(response(List.of(), List.of()));
        var devices = new DeviceConfiguration("a,b", "");
        var polling = new ScheduledPolling(importer, devices, history);
        polling.poll();
        var order = inOrder(client);
        order.verify(client).fetchHistoricalMeasurements("a");
        order.verify(client).fetchHistoricalMeasurements("b");
        polling.stop("a");
        polling.poll();
        assertEquals(1, history.summary("a").importSeqNum());
        assertEquals(2, history.summary("b").importSeqNum());
        polling.start("*");
        assertThrows(IllegalStateException.class, () -> new OneShotImport(importer, devices).run(null));
        assertEquals(2, history.summary("a").importSeqNum());
        assertEquals(3, history.summary("b").importSeqNum());
        polling.stop();
        assertTrue(polling.status("*").devices().stream().noneMatch(ScheduledPolling.Status::enabled));
    }
    @Test void migrationPreservesExistingReadingsWithNullSequence() {
        var source = jdbc.getDataSource();
        var flyway = org.flywaydb.core.Flyway.configure().dataSource(source)
                .schemas("migration_verification").defaultSchema("migration_verification")
                .target("1").load();
        flyway.migrate();
        jdbc.update("INSERT INTO migration_verification.measurement VALUES ('legacy', 'TEMPERATURE', CURRENT_TIMESTAMP, 12)");
        org.flywaydb.core.Flyway.configure().dataSource(source)
                .schemas("migration_verification").defaultSchema("migration_verification").load().migrate();
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM migration_verification.measurement WHERE import_seq_num IS NULL AND value=12", Integer.class));
    }

    @Test void unavailableHistoryReturns503() {
        var unavailable = mock(ExecutionHistory.class);
        when(unavailable.summary(anyString())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("secret"));
        var polling = new ScheduledPolling(importer, new DeviceConfiguration("a", ""), unavailable);
        assertEquals(503, assertThrows(ResponseStatusException.class, polling::status).getStatusCode().value());
    }
}
