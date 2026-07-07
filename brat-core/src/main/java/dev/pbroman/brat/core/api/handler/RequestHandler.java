package dev.pbroman.brat.core.api.handler;

import dev.pbroman.brat.core.api.RequestDefinition;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.data.result.HttpResponse;

/**
 * Executes a {@link RequestDefinition} against its protocol and returns the result — one
 * implementation per protocol (see {@link HttpRequestHandler} for HTTP).
 *
 * @param <T> the concrete {@link RequestDefinition} type this handler executes
 */
public interface RequestHandler<T extends RequestDefinition> {

    /**
     * Performs the request described by {@code requestDefinition} and returns the result.
     *
     * @param requestDefinition the request to perform
     * @return the result of performing the request; concrete handlers narrow this covariantly
     *         (e.g. {@link HttpRequestHandler} returns {@link HttpResponse})
     * @throws BratException if the request cannot be completed (e.g. a connection failure)
     */
    Object performRequest(T requestDefinition);

}
