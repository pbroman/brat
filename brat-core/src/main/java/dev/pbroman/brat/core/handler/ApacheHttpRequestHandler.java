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
import java.util.Set;

import dev.pbroman.brat.core.api.handler.HttpRequestHandler;
import dev.pbroman.brat.core.api.util.ArgsUtils;
import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.data.RequestOptions;
import dev.pbroman.brat.core.data.result.HttpResponse;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.util.HttpHeaderUtils;
import dev.pbroman.brat.core.util.Require;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.routing.SystemDefaultRoutePlanner;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.util.Timeout;

import static dev.pbroman.brat.core.util.Constants.DEFAULT_TIMEOUT_MS;
import static org.apache.hc.core5.http.HttpHeaders.CONTENT_TYPE;

/**
 * The {@link HttpRequestHandler} built on Apache HttpClient5.
 * <p>
 * Named for the client it wraps rather than for being the only one, because it will not be: a suite
 * selects a handler by name, and a suite needing network settings of its own, such as a client
 * certificate, is a differently configured handler rather than a different implementation. A proxy
 * and trusted certificates for the whole process need neither; see below.
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
 *
 * <h2>One request per call</h2>
 * <strong>Performing a request sends it exactly once.</strong> There is no transport-level retry: a
 * 429 or a 503 comes back as the response it is, and a connection that fails fails. Retrying is the
 * suite's to declare, through {@code repeatUntil} — which is the only version that counts its own
 * attempts, waits the interval the author chose, and reports what it did.
 * <p>
 * The client's own automatic retries are switched off rather than left at their default, because
 * they are invisible from above: the server would see two requests where the result records one,
 * {@code roundTripTimeMs} would carry a retry and its one-second wait, and a {@code POST} answered
 * 503 would be re-sent to a server that may have processed the first one.
 *
 * <h2>Cookies</h2>
 * <strong>This handler neither stores nor sends cookies.</strong> A {@code Set-Cookie} arrives as an
 * ordinary response header and is read like any other; a suite needing one on a later request
 * captures it with {@code setVars} and declares it as a {@code Cookie} header, which is visible in
 * the suite rather than implicit in the client.
 * <p>
 * The client's own cookie management is switched off rather than left at its default, because its
 * store belongs to the client and the client belongs to the process: a session established by one
 * run would be replayed in the next, and a suite would pass or fail depending on what ran before it.
 *
 * <h2>Proxies and trusted certificates</h2>
 * <strong>Both come from the JVM, through its standard properties, and nothing else.</strong>
 * <ul>
 *   <li><strong>Proxy</strong> — the JVM's default {@link java.net.ProxySelector}, asked on every
 *       request: {@code http.proxyHost}/{@code http.proxyPort} for {@code http} URLs,
 *       {@code https.proxyHost}/{@code https.proxyPort} for {@code https} ones, and
 *       {@code http.nonProxyHosts} for the hosts reached directly (by default {@code localhost} and
 *       the loopback addresses). Setting them after the handler is built still takes effect, as does
 *       replacing the default selector. The {@code HTTP_PROXY}/{@code HTTPS_PROXY} environment
 *       variables are not read.</li>
 *   <li><strong>A proxy that asks for a login is not supported.</strong> Its
 *       {@code 407 Proxy Authentication Required} comes back as the response.</li>
 *   <li><strong>Trusted certificates</strong> — the JVM's default trust store: {@code cacerts}, or the
 *       store {@code javax.net.ssl.trustStore} names, which <em>replaces</em> {@code cacerts} rather
 *       than adding to it ({@code javax.net.ssl.trustStorePassword} and {@code trustStoreType}
 *       alongside). Read when the handler is built, so they must be set before then. A certificate
 *       the store does not trust fails the handshake, which is a transport failure.</li>
 * </ul>
 * The client's own system-property support stays off, so {@code http.agent}, the JVM's default
 * credentials, {@code https.protocols} and {@code https.cipherSuites} do not apply. Properties the
 * JDK applies by itself, beneath the client, still do: {@code jdk.tls.client.protocols}, for
 * example, or DNS caching.
 */
public final class ApacheHttpRequestHandler implements HttpRequestHandler, AutoCloseable {

    /** The name a suite selects this handler by, and the one core makes the default for HTTP. */
    public static final String NAME = "httpclient5";

    /** The pool's total connection ceiling when none is given. */
    public static final int DEFAULT_MAX_TOTAL = 200;

    /** The pool's per-host connection ceiling when none is given. */
    public static final int DEFAULT_MAX_PER_ROUTE = 50;

    /** The statuses a redirect strategy follows; one still standing at the end was refused. */
    private static final Set<Integer> REDIRECT_STATUSES = Set.of(301, 302, 303, 307, 308);

    private static final String LOCATION = "Location";

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
                // Off deliberately: the client retries 429 and 503 once, a second apart, and
                // retries an idempotent request after an IOException - a second retry layer beneath
                // the one the suite authored, invisible to it. See the class Javadoc.
                .disableAutomaticRetries()
                // Off deliberately: HttpClient5 manages cookies unless told not to, in a store it
                // creates per client - so a session would outlive the run that established it. See
                // the class Javadoc for what a suite does instead.
                .disableCookieManagement()
                // Ours rather than the default: the default refuses a cross-host redirect only for
                // Authorization and Cookie, and compares host names but not ports.
                .setRedirectStrategy(new CredentialAwareRedirectStrategy())
                // No selector of its own, so the JVM's default is asked on every request. Narrower
                // than useSystemProperties(), which would also read http.agent, the system
                // credentials, https.protocols and the cipher suites. See the class Javadoc.
                .setRoutePlanner(new SystemDefaultRoutePlanner(null))
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setMaxConnTotal(maxTotal)
                        .setMaxConnPerRoute(maxPerRoute)
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setConnectTimeout(Timeout.ofMilliseconds(Long.parseLong(DEFAULT_TIMEOUT_MS)))
                                .build())
                        .build())
                .build();
    }

    @Override
    public String name() {
        return NAME;
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
     *   <li><strong>body</strong> — the body's payload is sent, UTF-8 encoded: a {@code raw} text as it
     *       is, a {@code file} body as it was read during interpolation, a {@code form} URL-encoded, with
     *       the {@code Content-Type} interpolation added. A {@code file} body that was never read fails.
     *       A body is sent on whatever
     *       method declares one, rather than only on POST/PUT/PATCH — silently dropping a body an
     *       author wrote is worse than letting the server reject it</li>
     *   <li><strong>timeout</strong> — {@code options}' {@link RequestOptions#timeoutMs()}, applied to
     *       awaiting a response and to waiting for a pooled connection. It is always a positive number
     *       of milliseconds, so a request is never sent without a ceiling.
     *       <p>
     *       ⚠ <strong>It does not bound connecting.</strong> HttpClient5 moved the connect timeout
     *       from {@code RequestConfig} to {@code ConnectionConfig}, which is held by the connection
     *       manager and resolvable only per route — nothing there can see an individual request. So
     *       connecting is bounded once per handler, at the same
     *       {@code DEFAULT_TIMEOUT_MS}, and a request declaring {@code timeout: 500} will still wait
     *       that long to reach an unreachable host. Left rather than worked around because the
     *       library's own default is three minutes, which is worse, and because connecting is
     *       arguably a property of the pooled route rather than of one request</li>
     *   <li><strong>redirects</strong> — not followed unless {@code options}'
     *       {@link RequestOptions#followRedirects()} says so; then the response is wherever the chain
     *       ends. A 301 or 302 answering a {@code POST}, and a 303 answering anything but {@code HEAD},
     *       is re-sent as a {@code GET} without its body; 307 and 308 keep method and body; every hop
     *       re-sends the request's headers. <strong>A redirect to another origin is refused when the
     *       request carries credentials</strong> — an {@code Authorization} or {@code Cookie} header,
     *       an {@code auth} other than {@code none}, any value built from a secret (url, header or
     *       body, since a 307 or 308 re-sends the body), or a form entry with a sensitive name; another
     *       origin is
     *       another scheme, host name or port. The 3xx is returned, with its {@code Location} as the response's
     *       {@code redirectNotFollowed}</li>
     * </ul>
     *
     * @param requestDefinition the interpolated request to perform
     * @param options what the run resolved for this request
     * @return the response: its status, every value of every header it carried, and its body, which
     *         is {@code null} when the response had none. Never {@code null}
     * @throws BratException if {@code requestDefinition} or {@code options} is {@code null}; if
     *         {@code url} is absent or not a valid URI; if a {@code file} body was never read, which only
     *         a definition that was never interpolated can carry; or if the request cannot be
     *         completed at all — connection refused, unknown host, failed TLS handshake, the
     *         timeout elapsing, or, when following redirects, more than 50 of them or a loop
     */
    @Override
    public HttpResponse performRequest(HttpRequestDefinition requestDefinition, RequestOptions options) {
        Require.nonNull(requestDefinition, "Cannot perform a null request definition");
        Require.nonNull(options, "Cannot perform a request without its options");

        // This handler takes no arguments at all, so any key is unknown. Ignoring them would make the
        // one handler everybody starts with the exception to what the author documents - and would
        // run a request without the mTLS settings its args asked another handler for.
        ArgsUtils.rejectUnknownArgs(requestDefinition.getArgs(), "the '" + NAME + "' request handler");

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
        context.setRequestConfig(configOf(options));
        var following = options.followRedirects();
        if (following) {
            context.setAttribute(
                    CredentialAwareRedirectStrategy.CARRIES_CREDENTIALS,
                    CredentialAwareRedirectStrategy.carriesCredentials(requestDefinition, options));
        }
        try {
            return client.execute(builder.build(), context, response -> toResponse(response, following));
        } catch (IOException e) {
            throw new BratException("Could not perform the request to " + requestDefinition.getUrl(), e);
        }
    }

    /**
     * Reads what came back, entirely, before the connection is released.
     *
     * @param response the live response
     * @param following whether the request followed redirects, which makes a final redirect one that
     *        was refused
     * @return the captured response
     * @throws IOException if the body could not be read
     */
    private static HttpResponse toResponse(ClassicHttpResponse response, boolean following) throws IOException {
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
        return new HttpResponse(response.getCode(), headers, body, following ? refusedRedirect(response) : null);
    }

    /**
     * The {@code Location} of a redirect the client did not follow, on a request that follows them.
     * <p>
     * Every other way a redirect chain stops — too many, a loop, an invalid {@code Location} — throws,
     * so a response that is still a 301, 302, 303, 307 or 308 with a {@code Location} is one the
     * redirect strategy refused.
     *
     * @param response the final response of a request that follows redirects
     * @return its {@code Location} as received, if it is a refused redirect; otherwise {@code null}
     */
    private static String refusedRedirect(ClassicHttpResponse response) {
        var location = response.getFirstHeader(LOCATION);
        return REDIRECT_STATUSES.contains(response.getCode()) && location != null ? location.getValue() : null;
    }

    /**
     * Parses the URL, failing here rather than letting the client report it in its own words.
     *
     * @param url the URL as authored and interpolated
     * @return the parsed URI
     * @throws BratException if {@code url} is blank or is not a valid URI
     */
    private static URI uriOf(String url) {
        Require.nonBlank(url, "A request needs a 'url'");
        try {
            return new URI(url);
        } catch (URISyntaxException e) {
            throw new BratException("The url '" + url + "' is not a valid URI", e);
        }
    }

    /**
     * The text to send as the body, or {@code null} when the request declares none.
     * <p>
     * The payload is the body's own, and nothing is read from disk: a {@code file} body has already
     * been read and interpolated before the definition reaches any handler.
     *
     * @param definition the interpolated request
     * @return the payload, or {@code null}
     * @throws BratException if a {@code file} body arrives unread, which only a definition that was
     *         never interpolated can be
     */
    private static String payloadOf(HttpRequestDefinition definition) {
        var body = definition.getBody();
        if (body == null) {
            return null;
        }
        var payload = body.getPayload();
        if (payload == null) {
            throw new BratException("The request's body file '" + body.getFile() + "' was never read");
        }
        return payload;
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
     * @param options the interpolated options, whose timeout is already defaulted and validated
     * @return the configuration for this one request
     */
    private static RequestConfig configOf(RequestOptions options) {
        var timeout = Timeout.ofMilliseconds(options.timeoutMs());
        // No connect timeout here: HttpClient5 moved it to ConnectionConfig, which is per connection
        // manager, so the constructor sets it once for the handler. See the note on performRequest.
        return RequestConfig.custom()
                .setRedirectsEnabled(options.followRedirects())
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
