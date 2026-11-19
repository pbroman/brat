package dev.pbroman.brat.integration.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;

/**
 * A forward proxy for plain HTTP that answers every request itself and records what it was asked
 * for.
 *
 * <p>It never forwards anything. A client sends a proxy the absolute URI
 * ({@code GET http://host/path}), so the recorded target says which host the request was meant for,
 * and the canned answer says it went through this proxy rather than anywhere else.
 */
public final class RecordingProxy implements AutoCloseable {

    /** The body of every answer. */
    public static final String ANSWER = "answered by the proxy";

    private final HttpServer server;
    private final AtomicReference<String> lastTarget = new AtomicReference<>();

    /** Starts the proxy on an ephemeral port on {@code localhost}. */
    public RecordingProxy() {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not start the recording proxy", e);
        }
        server.createContext("/", exchange -> {
            lastTarget.set(exchange.getRequestURI().toString());
            var body = ANSWER.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    /** @return the port to set as {@code http.proxyPort} */
    public int port() {
        return server.getAddress().getPort();
    }

    /** @return the absolute URI of the last request, or {@code null} if there has been none */
    public String lastTarget() {
        return lastTarget.get();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
