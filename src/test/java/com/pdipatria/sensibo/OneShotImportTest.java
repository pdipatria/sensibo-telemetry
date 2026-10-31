package com.pdipatria.sensibo;

import com.pdipatria.sensibo.service.OneShotImport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("import")
class OneShotImportTest extends PostgresIntegrationTest {
    static final StubSensiboServer SERVER;
    static {
        try {
            SERVER = new StubSensiboServer();
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @DynamicPropertySource
    static void apiProperties(DynamicPropertyRegistry registry) {
        registry.add("sensibo.api-base-url", SERVER::baseUrl);
        registry.add("sensibo.api-key", () -> "test-key");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired OneShotImport importer;

    @AfterAll
    static void stopServer() {
        SERVER.close();
    }

    @Test
    void startupImportsOnceAndRerunIsIdempotentAndApiFailureDoesNotWrite() {
        assertEquals(1, SERVER.requests.get());
        assertEquals("/api/v2/pods/test-device/historicalMeasurements", SERVER.lastRequest.getPath());
        assertEquals(2, count());
        assertEquals(2, jdbc.queryForObject(
                "SELECT count(*) FROM measurement WHERE device_id = 'test-device'", Integer.class));

        importer.run(new DefaultApplicationArguments());
        assertEquals(2, SERVER.requests.get());
        assertEquals(2, count());

        SERVER.status = 401;
        assertThrows(IllegalStateException.class, () -> importer.run(new DefaultApplicationArguments()));
        assertEquals(2, count());
        unsuccessfulApiPayloadIsRecordedAsFailed();
    }

    void unsuccessfulApiPayloadIsRecordedAsFailed() {
        SERVER.status = 200;
        String original = SERVER.body;
        try {
            SERVER.body = "{\"status\":\"error\",\"result\":null}";
            assertThrows(IllegalStateException.class, () -> importer.run(new DefaultApplicationArguments()));
            assertEquals("FAILED", jdbc.queryForObject("SELECT status FROM import_execution WHERE device_id='test-device' ORDER BY import_seq_num DESC LIMIT 1", String.class));
        } finally {
            SERVER.body = original;
            SERVER.status = 200;
        }
    }

    private int count() {
        return jdbc.queryForObject("SELECT count(*) FROM measurement", Integer.class);
    }
}
