package com.pdipatria.sensibo.model;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

class SensiboResponseTest {

    private final JsonMapper mapper = new JsonMapper();

    @Test
    void shouldDeserializeHistoricalMeasurements() throws Exception {
        try (var input = getClass().getResourceAsStream(
                "/sensibo/historical-measurements.json")) {

            assertNotNull(input, "Test JSON fixture not found");

            var response = mapper.readValue(
                input,
                SensiboResponse.class
            );

            assertEquals("success", response.status());
            assertNotNull(response.result());
            assertFalse(response.result().temperature().isEmpty());
            assertFalse(response.result().humidity().isEmpty());

            var first = response.result().temperature().get(0);

            assertEquals(18.1, first.value(), 0.001);
            assertNotNull(first.time());
        }
    }
}
