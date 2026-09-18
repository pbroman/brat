package dev.pbroman.brat.integration.support;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import dev.pbroman.brat.core.api.handler.HttpRequestHandler;
import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.data.result.HttpResponse;

/**
 * A second HTTP handler that answers without a server, so a suite can prove <em>which</em> handler
 * executed it.
 *
 * <p>This is the case handler selection exists for: several differently configured clients of one
 * protocol, live at once, chosen per request. A real one differs by proxy, trust store or client
 * certificate — differences a test could not observe from the outcome. This one differs by answering
 * {@code 299}, which a suite can assert on.
 *
 * <p>Wired by hand rather than discovered, unlike the stub protocol: both registration paths are worth
 * exercising, and a second <em>HTTP</em> handler appearing by discovery would change what every other
 * suite in this module runs through.
 */
public final class FixedResponseHttpHandler implements HttpRequestHandler {

    /** The status no real endpoint in this module returns, so seeing it means this handler ran. */
    public static final int STATUS = 299;

    private final AtomicInteger calls = new AtomicInteger();

    @Override
    public String name() {
        return "fixed";
    }

    @Override
    public HttpResponse performRequest(HttpRequestDefinition requestDefinition) {
        calls.incrementAndGet();
        return new HttpResponse(
                STATUS,
                Map.of("Content-Type", List.of("application/json")),
                "{\"handler\": \"fixed\", \"url\": \"" + requestDefinition.getUrl() + "\"}");
    }

    /** @return how many requests this handler was selected for */
    public int calls() {
        return calls.get();
    }
}
