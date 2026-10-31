package com.pdipatria.sensibo;

import com.pdipatria.sensibo.service.HistoricalImportService;
import com.pdipatria.sensibo.service.ScheduledPolling;
import com.pdipatria.sensibo.config.PollingConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScheduledPollingTest {
    @Test
    void stopAllowsInflightImportToFinishAndStartDoesNotOverlapIt() throws Exception {
        var importer = mock(HistoricalImportService.class);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return null;
        }).when(importer).importHistory("test-device");
        var polling = new ScheduledPolling(importer, new com.pdipatria.sensibo.config.DeviceConfiguration("", "test-device"), history());
        try (var executor = Executors.newSingleThreadExecutor()) {
            var inFlight = executor.submit(polling::poll);
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                assertTrue(polling.stop().devices().getFirst().running());
                assertFalse(polling.status().devices().getFirst().enabled());
                polling.poll();
                polling.start();
                polling.poll();
                verify(importer, times(1)).importHistory("test-device");
            } finally {
                release.countDown();
            }
            inFlight.get(5, TimeUnit.SECONDS);
        }
        assertFalse(polling.status().devices().getFirst().running());
        assertNotNull(polling.status().devices().getFirst().lastSucceededAt());
        polling.poll();
        verify(importer, times(2)).importHistory("test-device");
    }

    @Test
    void failureIsRecordedAndNextPollCanSucceed() {
        var importer = mock(HistoricalImportService.class);
        doThrow(new IllegalStateException("secret")).doNothing().when(importer).importHistory("test-device");
        var polling = new ScheduledPolling(importer, new com.pdipatria.sensibo.config.DeviceConfiguration("", "test-device"), history());
        assertDoesNotThrow(polling::poll);
        assertFalse(polling.status().devices().getFirst().running());
        assertNotNull(polling.status().devices().getFirst().lastFailedAt());
        polling.poll();
        assertNotNull(polling.status().devices().getFirst().lastSucceededAt());
    }

    private com.pdipatria.sensibo.service.ExecutionHistory history() {
        var history = mock(com.pdipatria.sensibo.service.ExecutionHistory.class);
        when(history.summary(anyString())).thenReturn(new com.pdipatria.sensibo.service.ExecutionHistory.Summary(0, 0, null));
        return history;
    }

    @Test
    void rejectsInvalidIntervalsAndConflictingProfiles() {
        var environment = new MockEnvironment();
        assertThrows(IllegalArgumentException.class,
                () -> new PollingConfiguration(environment, Duration.ZERO, Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new PollingConfiguration(environment, Duration.ofSeconds(-1), Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new PollingConfiguration(environment, Duration.ofMinutes(5), Duration.ofSeconds(-1)));
        environment.setActiveProfiles("import", "poll");
        assertThrows(IllegalStateException.class,
                () -> new PollingConfiguration(environment, Duration.ofMinutes(5), Duration.ZERO));
    }
}
