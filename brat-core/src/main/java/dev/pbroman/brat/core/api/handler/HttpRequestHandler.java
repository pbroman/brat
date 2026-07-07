package dev.pbroman.brat.core.api.handler;

import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.data.result.HttpResponse;
import dev.pbroman.brat.core.exception.BratException;

/**
 * The {@link RequestHandler} for HTTP requests.
 */
public interface HttpRequestHandler extends RequestHandler<HttpRequestDefinition> {

    /**
     * Performs the HTTP request described by {@code requestDefinition} and returns the response.
     *
     * @param requestDefinition the request to perform
     * @return the HTTP response
     * @throws BratException if the request cannot be completed (e.g. a connection failure) —
     *         see {@link RequestHandler#performRequest} for why this throws rather than
     *         returning a failure value
     */
    @Override
    HttpResponse performRequest(HttpRequestDefinition requestDefinition);

}
