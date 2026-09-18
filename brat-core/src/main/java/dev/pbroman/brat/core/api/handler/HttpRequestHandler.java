package dev.pbroman.brat.core.api.handler;

import java.util.Map;

import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.data.result.HttpResponse;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.handler.HttpResponseVars;

import static dev.pbroman.brat.core.util.Constants.HTTP;

/**
 * The {@link RequestHandler} for HTTP requests.
 * <p>
 * Everything true of HTTP rather than of one client is fixed here — the protocol name, the class an
 * authored {@code requestDefinition:} binds to, and the namespace a suite reads a response through —
 * so an implementation supplies only {@link #name()} and
 * {@link #performRequest(HttpRequestDefinition)}. Two HTTP handlers can then differ in how they send
 * a request and never in what a suite may say about one.
 */
public interface HttpRequestHandler extends RequestHandler<HttpRequestDefinition, HttpResponse> {

    @Override
    default String protocol() {
        return HTTP;
    }

    @Override
    default Class<HttpRequestDefinition> definitionType() {
        return HttpRequestDefinition.class;
    }

    @Override
    default Map<String, Object> responseVars(HttpResponse response) {
        return HttpResponseVars.of(response);
    }

    /**
     * Performs the HTTP request described by {@code requestDefinition} and returns the response.
     *
     * @param requestDefinition the request to perform
     * @return the HTTP response, whatever its status code — see
     *         {@link RequestHandler#performRequest} for why a 4xx or 5xx is a result rather than a
     *         failure
     * @throws BratException if the request could not be completed at all: a refused connection, an
     *         unknown host, a failed TLS handshake, or the handler's own timeout elapsing. Note the
     *         pair that reads confusingly and is not ambiguous — <em>their</em> 504 is a response,
     *         <em>our</em> timeout is not
     */
    @Override
    HttpResponse performRequest(HttpRequestDefinition requestDefinition);
}
