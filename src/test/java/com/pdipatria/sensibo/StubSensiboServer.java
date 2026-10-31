package com.pdipatria.sensibo;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

public class StubSensiboServer implements AutoCloseable {
    public static final String SUCCESS = """
        {"status":"success","result":{
          "temperature":[{"time":"2026-10-30T00:00:13Z","value":18.1}],
          "humidity":[{"time":"2026-10-30T00:00:13Z","value":62.5}]}}
        """;
    private final HttpServer server;
    public volatile int status = 200;
    public volatile String body = SUCCESS;
    public volatile URI lastRequest;
    public final AtomicInteger requests = new AtomicInteger();

    public StubSensiboServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastRequest = exchange.getRequestURI();
            requests.incrementAndGet();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
            exchange.close();
        });
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v2";
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
