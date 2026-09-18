package dev.pbroman.brat.integration.stub;

/**
 * What a stub request answers with — a protocol's own response type, which never leaves its handler.
 *
 * @param echoed what the request asked to be echoed
 * @param length how long it was, so a suite has a second thing to assert on
 */
public record StubResponse(String echoed, int length) {}
