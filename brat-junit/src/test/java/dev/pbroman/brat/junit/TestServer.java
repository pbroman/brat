package dev.pbroman.brat.junit;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * A small HTTP server for the bridge's tests, on a free port of {@code localhost}, recording every
 * request it receives.
 *
 * <ul>
 *   <li>{@code /ok} — {@code 200} with {@code {"ok": true}}</li>
 *   <li>{@code /status/<code>} — that status, with {@code {"status": <code>}}</li>
 *   <li>{@code /slow} — {@code 200} after {@link #SLOW_MS} milliseconds</li>
 * </ul>
 */
final class TestServer implements AutoCloseable {

    /** How long {@code /slow} takes to answer. */
    static final long SLOW_MS = 1500;

    private final HttpServer server;
    private final List<String> received = new CopyOnWriteArrayList<>();

    /** Starts the server. */
    TestServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/", this::handle);
        server.start();
    }

    /** The url to hand over as {@code env.baseUrl}. */
    String url() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    /** Every request received so far, as path and query, in the order received. */
    List<String> received() {
        return List.copyOf(received);
    }

    /** Forgets the requests received so far. */
    void clear() {
        received.clear();
    }

    private void handle(HttpExchange exchange) throws IOException {
        var uri = exchange.getRequestURI();
        received.add(uri.getRawQuery() == null ? uri.getRawPath() : uri.getRawPath() + "?" + uri.getRawQuery());
        var path = uri.getPath();
        int status = 200;
        String body = "{\"ok\": true}";
        if (path.startsWith("/status/")) {
            status = Integer.parseInt(path.substring("/status/".length()));
            body = "{\"status\": " + status + "}";
        } else if (path.equals("/slow")) {
            try {
                Thread.sleep(SLOW_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
