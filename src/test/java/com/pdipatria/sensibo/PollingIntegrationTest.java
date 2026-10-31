package com.pdipatria.sensibo;

import com.pdipatria.sensibo.service.ScheduledPolling;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"sensibo.poll.interval=PT0.1S", "sensibo.poll.initial-delay=PT0S"})
@ActiveProfiles("poll")
class PollingIntegrationTest extends PostgresIntegrationTest {
    static final StubSensiboServer SERVER;
    static {
        try {
            SERVER = new StubSensiboServer();
            SERVER.status = 429;
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @DynamicPropertySource
    static void apiProperties(DynamicPropertyRegistry registry) {
        registry.add("sensibo.api-base-url", SERVER::baseUrl);
        registry.add("sensibo.api-key", () -> "test-key");
    }

    @Autowired ScheduledPolling polling;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper mapper;
    @Value("${local.server.port}") int port;

    @AfterAll
    static void stopServer() {
        SERVER.close();
    }

    @Test
    void schedulerRecoversFromApiFailureAndControlApiPausesAndResumesWithoutDuplicates() throws Exception {
        await().atMost(Duration.ofSeconds(10)).until(() -> polling.status().devices().getFirst().lastFailedAt() != null);
        assertEquals(0, count());
        var stopped = command("stop");
        assertFalse(stopped.enabled());
        await().atMost(Duration.ofSeconds(5)).until(() -> !polling.status().devices().getFirst().running());
        int pausedRequests = SERVER.requests.get();
        await().during(Duration.ofMillis(350)).atMost(Duration.ofSeconds(2))
                .until(() -> SERVER.requests.get() == pausedRequests);
        assertFalse(command("status").enabled());

        SERVER.status = 200;
        assertTrue(command("start").enabled());
        assertTrue(command("start").enabled());
        await().atMost(Duration.ofSeconds(10)).until(() -> count() == 2
                && SERVER.requests.get() >= pausedRequests + 3);
        command("stop");
        await().atMost(Duration.ofSeconds(5)).until(() -> !polling.status().devices().getFirst().running());
        assertEquals("test-device", command("status/test-device").deviceId());
        assertFalse(command("stop/*").enabled());
        try (var client = HttpClient.newHttpClient()) {
            var unknown = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/polling/status/unknown")).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(404, unknown.statusCode());
        }
        var status = command("status");
        assertFalse(status.enabled());
        assertNotNull(status.lastStartedAt());
        assertNotNull(status.lastSucceededAt());
        assertNotNull(status.lastFailedAt());
        assertEquals(2, count());
        assertEquals(2, jdbc.queryForObject(
                "SELECT count(*) FROM measurement WHERE device_id = 'test-device'", Integer.class));
    }

    private ScheduledPolling.Status command(String action) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/polling/" + action))
                .timeout(Duration.ofSeconds(5));
        if (!action.startsWith("status")) {
            request.POST(HttpRequest.BodyPublishers.noBody());
        }
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            return mapper.readValue(response.body(), ScheduledPolling.Envelope.class).devices().getFirst();
        }
    }

    private int count() {
        return jdbc.queryForObject("SELECT count(*) FROM measurement", Integer.class);
    }
}
