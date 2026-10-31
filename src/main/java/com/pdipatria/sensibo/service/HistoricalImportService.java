package com.pdipatria.sensibo.service;

import com.pdipatria.sensibo.client.SensiboClient;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile({"import", "poll"})
public class HistoricalImportService {
    private final SensiboClient client;
    private final MeasurementIngestionService ingestion;
    private final ExecutionHistory history;
    public HistoricalImportService(SensiboClient client, MeasurementIngestionService ingestion, ExecutionHistory history) {
        this.client = client;
        this.ingestion = ingestion;
        this.history = history;
    }
    // One importing process is supported. Serialize even callers outside the scheduler.
    private final Object importLock = new Object();
    public void importHistory(String deviceId) {
        synchronized (importLock) { importDevice(deviceId); }
    }
    private void importDevice(String deviceId) {
        int seq = history.begin(deviceId);
        boolean fetched = false;
        try {
            var response = client.fetchHistoricalMeasurements(deviceId);
            fetched = true;
            ingestion.ingest(deviceId, seq, response);
        } catch (RuntimeException ex) {
            String reason = fetched ? "Measurement ingestion failed" : "Sensibo request or response failed";
            try { history.fail(deviceId, seq, reason); }
            catch (RuntimeException recordingFailure) { /* Database may be unavailable. */ }
            throw new IllegalStateException(reason);
        }
    }
}
