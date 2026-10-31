package com.pdipatria.sensibo;

import com.pdipatria.sensibo.client.SensiboClient;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class SensiboClientTest {
    @Test
    void requestsConfiguredDeviceKeyAndDaysAndDeserializesMeasurements() throws Exception {
        try (var server = new StubSensiboServer()) {
            var response = client(server).fetchHistoricalMeasurements("device/one");
            assertEquals("/api/v2/pods/device%2Fone/historicalMeasurements", server.lastRequest.getRawPath());
            assertEquals("apiKey=secret%2B%26key&days=2", server.lastRequest.getRawQuery());
            assertEquals(Instant.parse("2026-10-30T00:00:13Z"), response.result().temperature().getFirst().time());
            assertEquals(18.1, response.result().temperature().getFirst().value());
            assertEquals(62.5, response.result().humidity().getFirst().value());
            assertEquals(1, server.requests.get());
        }
    }

    @Test
    void reportsHttpErrorsWithoutLeakingSecretsOrRetrying() throws Exception {
        try (var server = new StubSensiboServer()) {
            for (int status : new int[]{401, 429, 500, 302}) {
                server.status = status;
                server.body = "secret+&key";
                var error = assertThrows(IllegalStateException.class,
                        () -> client(server).fetchHistoricalMeasurements("device/one"));
                assertEquals("Sensibo request failed (HTTP " + status + ")", error.getMessage());
                assertNull(error.getCause());
            }
            assertEquals(4, server.requests.get());
        }
    }

    @Test
    void rejectsMalformedEmptyAndUnsuccessfulResponses() throws Exception {
        try (var server = new StubSensiboServer()) {
            for (String body : new String[]{"secret+&key", "", "null", "{}",
                    "{\"status\":\"error\",\"result\":null}",
                    "{\"status\":\"success\",\"result\":{\"temperature\":[]}}"}) {
                server.body = body;
                var error = assertThrows(IllegalStateException.class,
                        () -> client(server).fetchHistoricalMeasurements("device/one"));
                assertFalse(error.getMessage().contains("secret"));
                assertNull(error.getCause());
            }
        }
    }

    @Test
    void rejectsMissingCredentialsAndInvalidDaysBeforeRequest() {
        var mapper = new JsonMapper();
        assertThrows(IllegalArgumentException.class,
                () -> new SensiboClient(mapper, " ", 1, "http://localhost"));
        assertThrows(IllegalArgumentException.class,
                () -> new SensiboClient(mapper, "key", 0, "http://localhost"));
    }

    private SensiboClient client(StubSensiboServer server) {
        return new SensiboClient(new JsonMapper(), "secret+&key", 2, server.baseUrl());
    }
}
