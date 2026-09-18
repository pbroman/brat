package dev.pbroman.brat.integration.stub;

import java.util.Map;

import dev.pbroman.brat.core.api.handler.RequestHandler;

/**
 * Executes {@code protocol: stub} requests, by echoing them.
 *
 * <p>Found through {@code ServiceLoader}: {@code META-INF/services/…RequestHandler} names it, and a
 * runner built anywhere in this module picks it up with no wiring at all. That is the registration
 * path a plugin jar uses, exercised here without one.
 *
 * <p>Real plugins declare a protocol sub-interface so that several handlers for one protocol agree on
 * these three answers. One handler needs none, so this is deliberately the bare shape.
 */
public final class StubRequestHandler implements RequestHandler<StubRequestDefinition, StubResponse> {

    @Override
    public String protocol() {
        return "stub";
    }

    @Override
    public String name() {
        return "echo";
    }

    @Override
    public Class<StubRequestDefinition> definitionType() {
        return StubRequestDefinition.class;
    }

    @Override
    public StubResponse performRequest(StubRequestDefinition requestDefinition) {
        var said = requestDefinition.getSay();
        return new StubResponse(said, said == null ? 0 : said.length());
    }

    @Override
    public Map<String, Object> responseVars(StubResponse response) {
        return Map.of("echoed", response.echoed(), "length", response.length());
    }
}
