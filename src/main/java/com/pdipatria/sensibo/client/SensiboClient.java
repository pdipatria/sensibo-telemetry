package com.pdipatria.sensibo.client;

import com.pdipatria.sensibo.model.SensiboResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
@Profile({"import", "poll"})
public class SensiboClient {
    private final JsonMapper mapper;
    private final String apiKey;
    private final int days;
    private final String baseUrl;

    public SensiboClient(JsonMapper mapper,
            @Value("${sensibo.api-key}") String apiKey,
            @Value("${sensibo.history-days:1}") int days,
            @Value("${sensibo.api-base-url:https://home.sensibo.com/api/v2}") String baseUrl) {
        Assert.hasText(apiKey, "sensibo.api-key must not be blank");
        Assert.isTrue(days > 0, "sensibo.history-days must be positive");
        this.mapper = mapper;
        this.apiKey = apiKey;
        this.days = days;
        this.baseUrl = baseUrl;
    }

    public SensiboResponse fetchHistoricalMeasurements(String deviceId) {
        var uri = UriComponentsBuilder.fromUriString(baseUrl)
                .pathSegment("pods", "{deviceId}", "historicalMeasurements")
                .queryParam("apiKey", "{apiKey}").queryParam("days", days)
                .encode().buildAndExpand(deviceId, apiKey).toUri();
        var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60))
                .header("Accept", "application/json").GET().build();
        // Never include the request URI, response body, or underlying exception:
        // the API authenticates via a query parameter and errors can echo it.
        try (var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build()) {
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Sensibo request failed (HTTP " + response.statusCode() + ")");
            }
            SensiboResponse result;
            try {
                result = mapper.readValue(response.body(), SensiboResponse.class);
            } catch (RuntimeException ex) {
                throw new IllegalStateException("Sensibo returned invalid measurement JSON");
            }
            if (result == null || !"success".equals(result.status()) || result.result() == null
                    || result.result().temperature() == null || result.result().humidity() == null) {
                throw new IllegalStateException("Sensibo returned an unsuccessful or incomplete measurement response");
            }
            return result;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Sensibo request interrupted");
        } catch (IOException ex) {
            throw new IllegalStateException("Sensibo request failed due to a network error or timeout");
        }
    }
}
