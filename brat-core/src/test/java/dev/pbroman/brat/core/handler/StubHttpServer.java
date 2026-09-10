package dev.pbroman.brat.core.handler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * A throwaway HTTP server for handler tests, built on the JDK's own so that testing an HTTP client
 * needs no second HTTP library.
 * <p>
 * Binds an ephemeral port, so tests never collide and nothing has to be reserved. What each test
 * needs the server to do is set per test through {@link #respond}; what the server actually received
 * is read back through {@link #lastRequest()}, which is how a test asserts on something that left the
 * process.
 */
final class StubHttpServer implements AutoCloseable {

    private final HttpServer server;
    private final AtomicReference<Consumer<HttpExchange>> behaviour = new AtomicReference<>();
    private final AtomicReference<ReceivedRequest> received = new AtomicReference<>();

    /** What the stub saw, so a test can assert on what was actually sent. */
    record ReceivedRequest(String method, String path, Map<String, List<String>> headers, String body) {}

    StubHttpServer() {
        try {
            server = HttpServer.create(new InetSocketAddress(0), 0);
        } catch (IOException e) {
            throw new IllegalStateException("Could not start the stub server", e);
        }
        server.createContext("/", this::handle);
        server.start();
    }

    /** @return the base URL the stub is listening on, e.g. {@code http://localhost:54321} */
    String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    /**
     * Sets what the stub does with the next request.
     *
     * @param status the status code to return
     * @param body the body to return, or {@code null} for none
     * @param headers response headers to add, as alternating name/value pairs
     */
    void respond(int status, String body, String... headers) {
        behaviour.set(exchange -> {
            try {
                for (var i = 0; i + 1 < headers.length; i += 2) {
                    exchange.getResponseHeaders().add(headers[i], headers[i + 1]);
                }
                var bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, body == null ? -1 : bytes.length);
                if (body != null) {
                    exchange.getResponseBody().write(bytes);
                }
                exchange.close();
            } catch (IOException e) {
                throw new IllegalStateException("Stub failed to respond", e);
            }
        });
    }

    /**
     * Makes the stub sleep before responding, for timeout tests.
     *
     * @param millis how long to sleep
     */
    void respondSlowly(long millis) {
        var previous = behaviour.get();
        behaviour.set(exchange -> {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            previous.accept(exchange);
        });
    }

    /** @return the request the stub last received, or {@code null} if it has had none */
    ReceivedRequest lastRequest() {
        return received.get();
    }

    private void handle(HttpExchange exchange) throws IOException {
        var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        var headers = new java.util.LinkedHashMap<String, List<String>>();
        for (var entry : exchange.getRequestHeaders().entrySet()) {
            headers.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        received.set(new ReceivedRequest(
                exchange.getRequestMethod(), exchange.getRequestURI().getPath(), headers, body));
        var current = behaviour.get();
        if (current == null) {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            return;
        }
        current.accept(exchange);
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
