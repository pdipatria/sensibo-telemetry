package com.pdipatria.sensibo.service;

import com.pdipatria.sensibo.config.DeviceConfiguration;
import java.time.Instant;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Component
@Profile("poll")
public class ScheduledPolling {
    private final HistoricalImportService importer;
    private final ExecutionHistory history;
    private final Map<String, State> states = new LinkedHashMap<>();
    private boolean cycling;
    private static class State {
        boolean enabled = true;
        boolean running;
        Instant started, succeeded, failed;
    }
    public ScheduledPolling(HistoricalImportService importer, DeviceConfiguration devices, ExecutionHistory history) {
        this.importer = importer;
        this.history = history;
        devices.ids().forEach(id -> states.put(id, new State()));
    }
    @Scheduled(fixedDelayString = "${sensibo.poll.interval:PT5M}", initialDelayString = "${sensibo.poll.initial-delay:PT10S}")
    public void poll() {
        synchronized (this) {
            if (cycling) return;
            cycling = true;
        }
        try {
            for (var entry : states.entrySet()) {
                var state = entry.getValue();
                synchronized (this) {
                    if (!state.enabled) continue;
                    state.running = true;
                    state.started = Instant.now();
                }
                try {
                    importer.importHistory(entry.getKey());
                    synchronized (this) { state.succeeded = Instant.now(); }
                } catch (RuntimeException ex) {
                    org.slf4j.LoggerFactory.getLogger(getClass()).warn("Sensibo poll failed; will retry at the next polling interval");
                    synchronized (this) { state.failed = Instant.now(); }
                } finally {
                    synchronized (this) { state.running = false; }
                }
            }
        } finally {
            synchronized (this) { cycling = false; }
        }
    }
    public Envelope start() { return start("*"); }
    public Envelope stop() { return stop("*"); }
    public Envelope status() { return status("*"); }
    public synchronized Envelope start(String device) {
        selected(device).forEach(id -> states.get(id).enabled = true);
        return status(device);
    }
    public synchronized Envelope stop(String device) {
        selected(device).forEach(id -> states.get(id).enabled = false);
        return status(device);
    }
    private List<String> selected(String device) {
        if ("*".equals(device)) return List.copyOf(states.keySet());
        if (!states.containsKey(device)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unconfigured device");
        return List.of(device);
    }
    public synchronized Envelope status(String device) {
        var selected = selected(device);
        try {
            var result = new ArrayList<Status>();
            for (String id : selected) {
                var state = states.get(id);
                var summary = history.summary(id);
                result.add(new Status(id, state.enabled, state.running, state.started, state.succeeded, state.failed,
                        summary.importSeqNum(), summary.lastSuccessfulImportSeqNum(), summary.lastImportStatus()));
            }
            return new Envelope(List.copyOf(result));
        } catch (org.springframework.dao.DataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Execution history unavailable");
        }
    }
    public record Envelope(List<Status> devices) {}
    public record Status(String deviceId, boolean enabled, boolean running, Instant lastStartedAt,
                         Instant lastSucceededAt, Instant lastFailedAt, int importSeqNum,
                         int lastSuccessfulImportSeqNum, String lastImportStatus) {}
}
