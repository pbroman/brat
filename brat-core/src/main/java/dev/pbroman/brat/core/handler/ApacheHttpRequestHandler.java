package dev.pbroman.brat.core.handler;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import dev.pbroman.brat.core.api.handler.HttpRequestHandler;
import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.data.result.HttpResponse;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.util.HttpHeaderUtils;
import dev.pbroman.brat.core.util.Require;
import dev.pbroman.brat.core.util.ResourceReader;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.util.Timeout;

import static dev.pbroman.brat.core.util.Constants.BODY_STRING;
import static dev.pbroman.brat.core.util.Constants.DEFAULT_TIMEOUT_MS;
import static dev.pbroman.brat.core.util.Constants.FILE_BODY;
import static org.apache.hc.core5.http.HttpHeaders.CONTENT_TYPE;

/**
 * The {@link HttpRequestHandler} built on Apache HttpClient5.
 * <p>
 * Named for the client it wraps rather than for being the only one, because it will not be: a suite
 * selects a handler by name, and a proxied, mTLS or self-signed-certificate suite is a differently
 * configured handler rather than a different implementation.
 *
 * <h2>What throws and what does not</h2>
 * <strong>The line is whether a response exists at all, not what its status says.</strong> Every
 * status code — including 4xx, and including the gateway family 502/503/504 — comes back as an
 * {@link HttpResponse}, because each is something a server said and each carries a body worth
 * asserting on. A handler that threw on 5xx would also be guessing: a 503 from a load balancer and a
 * 503 from an application in maintenance mode are indistinguishable from here, and a suite testing
 * rate limiting or circuit-breaker behaviour expects to see one.
 * <p>
 * Transport failure throws, because there is no response to return: connection refused, unknown host,
 * a TLS handshake that fails, or this handler's own timeout elapsing. Note the pair that reads
 * confusingly and is not ambiguous — <em>their</em> 504 Gateway Timeout is a response, <em>our</em>
 * timeout is not.
 *
 * <h2>Connection pooling</h2>
 * One pooled client is built per handler and shared by every request it performs. HttpClient5's
 * client and pooling connection manager are both documented thread-safe, so this is safe once
 * execution goes parallel — but the pool's <em>size</em> is what silently caps it, which is why the
 * limits are constructor arguments rather than inherited. The library's own defaults (25 total, 5 per
 * route) throttle a suite hammering one host at five concurrent requests, blocking the sixth with
 * nothing logged, which would make a performance run measure its own queue.
 */
public final class ApacheHttpRequestHandler implements HttpRequestHandler, AutoCloseable {

    /** The pool's total connection ceiling when none is given. */
    public static final int DEFAULT_MAX_TOTAL = 200;

    /** The pool's per-host connection ceiling when none is given. */
    public static final int DEFAULT_MAX_PER_ROUTE = 50;

    private final CloseableHttpClient client;

    /**
     * Constructs a handler whose pool is generous enough not to throttle a parallel run.
     * <p>
     * Deliberately not HttpClient5's own 25/5: the costs are asymmetric. Connections are created
     * lazily, so a ceiling far above what a sequential run uses costs nothing, while a ceiling below
     * what a parallel run needs is invisible and is misread as a slow server.
     */
    public ApacheHttpRequestHandler() {
        this(DEFAULT_MAX_TOTAL, DEFAULT_MAX_PER_ROUTE);
    }

    /**
     * Constructs a handler with explicit pool limits, for a suite whose concurrency the defaults do
     * not fit.
     *
     * @param maxTotal the maximum number of pooled connections across all hosts; must be positive
     * @param maxPerRoute the maximum number of pooled connections to any one host; must be positive
     *        and no greater than {@code maxTotal}, since a per-host ceiling above the total ceiling
     *        cannot be reached and states an intent the pool will not honour
     * @throws BratException if either value is not positive, or if {@code maxPerRoute} exceeds
     *         {@code maxTotal}
     */
    public ApacheHttpRequestHandler(int maxTotal, int maxPerRoute) {
        if (maxTotal <= 0 || maxPerRoute <= 0) {
            throw new BratException(String.format(
                    "A connection pool needs positive limits, got maxTotal %d and maxPerRoute %d",
                    maxTotal, maxPerRoute));
        }
        if (maxPerRoute > maxTotal) {
            throw new BratException(String.format(
                    "maxPerRoute %d exceeds maxTotal %d, so the per-host limit could never be reached",
                    maxPerRoute, maxTotal));
        }
        this.client = HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setMaxConnTotal(maxTotal)
                        .setMaxConnPerRoute(maxPerRoute)
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setConnectTimeout(Timeout.ofMilliseconds(Long.parseLong(DEFAULT_TIMEOUT_MS)))
                                .build())
                        .build())
                .build();
    }

    /**
     * Performs the request and returns what the server said.
     * <p>
     * The definition arrives already interpolated, so every value here is final text. What this does
     * with each:
     * <ul>
     *   <li><strong>method</strong> — upper-cased, then sent. HTTP methods are case-sensitive tokens
     *       and every standard one is upper case, so {@code method: post} is a spelling rather than a
     *       different method, and rejecting it would be pedantry an author cannot act on. Never
     *       {@code null}, having defaulted to {@code GET} when the definition was constructed</li>
     *   <li><strong>url</strong> — sent as given, once it parses as a URI</li>
     *   <li><strong>headers</strong> — sent as given. Names differing only in case were already
     *       rejected when the definition was constructed</li>
     *   <li><strong>body</strong> — the {@code _bodyString} entry is sent as the payload, UTF-8
     *       encoded. It was derived at construction for a {@code raw} body and for form-encoded
     *       entries; a {@code file} body is resolved <em>here</em>, after interpolation, so that both
     *       the path and the file's content may hold {@code ${…}} tokens. A body is sent on whatever
     *       method declares one, rather than only on POST/PUT/PATCH — silently dropping a body an
     *       author wrote is worse than letting the server reject it</li>
     *   <li><strong>timeout</strong> — parsed as a whole number of milliseconds and applied to
     *       awaiting a response and to waiting for a pooled connection. Absent, it is
     *       {@link dev.pbroman.brat.core.util.Constants#DEFAULT_TIMEOUT_MS}, and zero or negative is
     *       rejected rather than taken as HttpClient5 takes it, which is infinite. A request with no
     *       ceiling at all is not offered: an unbounded wait is indistinguishable from a hang. The
     *       cascade may fill a request's {@code timeout} from an ancestor before it reaches here;
     *       this applies when nothing did.
     *       <p>
     *       ⚠ <strong>It does not bound connecting.</strong> HttpClient5 moved the connect timeout
     *       from {@code RequestConfig} to {@code ConnectionConfig}, which is held by the connection
     *       manager and resolvable only per route — nothing there can see an individual request. So
     *       connecting is bounded once per handler, at the same
     *       {@code DEFAULT_TIMEOUT_MS}, and a request declaring {@code timeout: 500} will still wait
     *       that long to reach an unreachable host. Left rather than worked around because the
     *       library's own default is three minutes, which is worse, and because connecting is
     *       arguably a property of the pooled route rather than of one request</li>
     *   <li><strong>auth</strong> — <em>ignored</em>. A declared {@code auth:} binds and interpolates
     *       and is then inert: nothing applies it to the request. Stated because a silently inert
     *       credential is the kind of thing a suite passes green without</li>
     * </ul>
     *
     * @param requestDefinition the interpolated request to perform
     * @return the response: its status, every value of every header it carried, and its body, which
     *         is {@code null} when the response had none. Never {@code null}
     * @throws BratException if {@code requestDefinition} is {@code null}; if {@code url} is absent or
     *         not a valid URI; if {@code timeout} is set and is not a whole number of milliseconds;
     *         if a {@code file} body cannot be read, naming the path; if a body is declared but holds
     *         nothing to send, which means entries with neither a {@code raw} nor a {@code file} key
     *         nor a form-encoded {@code Content-Type} to join them under; or if the request cannot be
     *         completed at all — connection refused, unknown host, failed TLS handshake, or the
     *         timeout elapsing
     */
    @Override
    public HttpResponse performRequest(HttpRequestDefinition requestDefinition) {
        Require.nonNull(requestDefinition, "Cannot perform a null request definition");

        var builder = ClassicRequestBuilder.create(requestDefinition.getMethod().toUpperCase(Locale.ROOT))
                .setUri(uriOf(requestDefinition.getUrl()));
        if (requestDefinition.getHeaders() != null) {
            for (var header : requestDefinition.getHeaders().entrySet()) {
                builder.addHeader(header.getKey(), header.getValue());
            }
        }
        var payload = payloadOf(requestDefinition);
        if (payload != null) {
            // UTF-8 explicitly, and the content type only where the author declared one: the entity
            // would otherwise contribute a second Content-Type beside the header they wrote.
            builder.setEntity(
                    new ByteArrayEntity(payload.getBytes(StandardCharsets.UTF_8), contentTypeOf(requestDefinition)));
        }

        var context = HttpClientContext.create();
        context.setRequestConfig(configOf(requestDefinition));
        try {
            return client.execute(builder.build(), context, ApacheHttpRequestHandler::toResponse);
        } catch (IOException e) {
            throw new BratException("Could not perform the request to " + requestDefinition.getUrl(), e);
        }
    }

    /**
     * Reads what came back, entirely, before the connection is released.
     *
     * @param response the live response
     * @return the captured response
     * @throws IOException if the body could not be read
     */
    private static HttpResponse toResponse(ClassicHttpResponse response) throws IOException {
        var headers = new LinkedHashMap<String, List<String>>();
        for (var header : response.getHeaders()) {
            headers.computeIfAbsent(header.getName(), name -> new ArrayList<>()).add(header.getValue());
        }
        String body = null;
        if (response.getEntity() != null) {
            try {
                body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            } catch (ParseException e) {
                throw new BratException("Could not read the response body", e);
            }
        }
        return new HttpResponse(response.getCode(), headers, body);
    }

    /**
     * Parses the URL, failing here rather than letting the client report it in its own words.
     *
     * @param url the URL as authored and interpolated
     * @return the parsed URI
     * @throws BratException if {@code url} is blank or is not a valid URI
     */
    private static URI uriOf(String url) {
        if (StringUtils.isBlank(url)) {
            throw new BratException("A request needs a 'url'");
        }
        try {
            return new URI(url);
        } catch (URISyntaxException e) {
            throw new BratException("The url '" + url + "' is not a valid URI", e);
        }
    }

    /**
     * The text to send as the body, or {@code null} when the request declares none.
     * <p>
     * A {@code file} body is read here rather than at construction, so that both the path and the
     * file's content may hold {@code ${…}} tokens resolved by then.
     *
     * @param definition the interpolated request
     * @return the payload, or {@code null}
     * @throws BratException if a {@code file} body cannot be read, or if a body is declared with
     *         nothing to send
     */
    private static String payloadOf(HttpRequestDefinition definition) {
        var body = definition.getBody();
        if (body == null || body.isEmpty()) {
            return null;
        }
        if (body.get(BODY_STRING) != null) {
            return body.get(BODY_STRING);
        }
        if (body.get(FILE_BODY) != null) {
            return ResourceReader.readFileToString(body.get(FILE_BODY), StandardCharsets.UTF_8);
        }
        throw new BratException("The request declares a body with nothing to send. Entries other than 'raw' or 'file' "
                + "are only joined into a payload under a form-urlencoded 'Content-Type'");
    }

    /**
     * The content type to attach to the entity, taken only from what the author declared.
     *
     * @param definition the interpolated request
     * @return the parsed content type, or {@code null} when no {@code Content-Type} was declared
     * @throws BratException if a {@code Content-Type} was declared and cannot be parsed
     */
    private static ContentType contentTypeOf(HttpRequestDefinition definition) {
        var declared = HttpHeaderUtils.get(definition.getHeaders(), CONTENT_TYPE);
        if (StringUtils.isBlank(declared)) {
            return null;
        }
        try {
            return ContentType.parse(declared);
        } catch (UnsupportedCharsetException e) {
            throw new BratException("The Content-Type '" + declared + "' names a charset this JVM has not", e);
        }
    }

    /**
     * Builds the per-request configuration, which is where the timeout lands.
     *
     * @param definition the interpolated request
     * @return the configuration for this one request
     * @throws BratException if {@code timeout} is set and is not a whole number of milliseconds
     */
    private static RequestConfig configOf(HttpRequestDefinition definition) {
        var declared = StringUtils.defaultIfBlank(definition.getTimeout(), DEFAULT_TIMEOUT_MS);
        long millis;
        try {
            millis = Long.parseLong(declared.trim());
        } catch (NumberFormatException e) {
            throw new BratException("The timeout '" + declared + "' is not a whole number of milliseconds", e);
        }
        if (millis <= 0) {
            throw new BratException("The timeout '" + declared + "' must be a positive number of milliseconds");
        }
        var timeout = Timeout.ofMilliseconds(millis);
        // No connect timeout here: HttpClient5 moved it to ConnectionConfig, which is per connection
        // manager, so the constructor sets it once for the handler. See the note on performRequest.
        return RequestConfig.custom()
                .setResponseTimeout(timeout)
                .setConnectionRequestTimeout(timeout)
                .build();
    }

    /**
     * Closes the pooled client and every connection it holds.
     * <p>
     * Nothing in {@code brat-core} currently owns a handler long enough to call this; it exists
     * because a pool with no way to release its connections is a leak by construction, and whoever
     * manages handler lifecycle is expected to call it.
     *
     * @throws BratException if the client could not be closed
     */
    @Override
    public void close() {
        try {
            client.close();
        } catch (IOException e) {
            throw new BratException("Could not close the HTTP client", e);
        }
    }
}
