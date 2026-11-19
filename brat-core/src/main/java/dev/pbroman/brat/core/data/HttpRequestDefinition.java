package dev.pbroman.brat.core.data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import dev.pbroman.brat.core.api.data.RequestDefinition;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.util.HttpHeaderUtils;
import lombok.Getter;

import static dev.pbroman.brat.core.util.Constants.DEFAULT_METHOD;
import static dev.pbroman.brat.core.util.Constants.HTTP;
import static dev.pbroman.brat.core.util.Constants.TOKEN_PREFIX;
import static org.apache.hc.core5.http.ContentType.APPLICATION_FORM_URLENCODED;
import static org.apache.hc.core5.http.HttpHeaders.CONTENT_TYPE;

/**
 * The default, HTTP-based {@link RequestDefinition} implementation.
 * <p>
 * {@code final}: a plugin adding a protocol writes its own {@code RequestDefinition} implementation
 * beside this one rather than extending it, and "HTTP plus one extra knob" is the per-request handler
 * {@code args} bag rather than a subclass.
 * <p>
 * It holds only what is sent. How long to wait for the answer is the request's {@code timeout}, which
 * reaches the handler resolved in {@link RequestOptions}.
 */
@Getter
public final class HttpRequestDefinition extends ConfigData implements RequestDefinition {

    private final String url;
    private final String method;
    private final HttpBody body;
    private final Map<String, String> headers;
    private final Map<String, String> args;

    @Override
    public String protocol() {
        return HTTP;
    }

    /**
     * Constructs a request definition.
     * <p>
     * <strong>A form body and its {@code Content-Type}.</strong> A form is sent as
     * {@code application/x-www-form-urlencoded}. When {@code body} is a form and {@code headers} declare
     * a {@code Content-Type} holding no {@code ${…}} token, its media type — compared ignoring case and
     * any parameters such as {@code ; charset=UTF-8} — must be that one. A {@code Content-Type} holding a
     * token is not checked here; it is checked on the interpolated copy, which is constructed with the
     * resolved value. An absent {@code Content-Type} is left absent: interpolation adds it.
     *
     * @param url the request URL, possibly holding {@code ${...}} tokens
     * @param method the HTTP method, or {@code null} for {@code GET} — the convention every
     *        comparable tool follows, and better than binding a null that fails at request time
     * @param body the body, or {@code null} for none
     * @param headers the request headers, or {@code null}
     * @param args extra arguments for the handler that performs this request, or {@code null} for
     *        none. Their keys belong to that handler, not to HTTP — which is why they are a bag and
     *        not fields, and why the handler is what rejects one it does not know
     * @param outcomes the interpolation outcomes of an interpolated copy, or {@code null} on an
     *        as-authored instance
     * @throws BratException if two header names differ only in case. They are one header to HTTP and
     *         two keys to YAML, so declaring both states two values for one header, and it fails
     *         whether or not anything reads that header
     * @throws BratException if {@code body} is a form and a token-free {@code Content-Type} names another
     *         media type. The message names the type that was declared
     */
    public HttpRequestDefinition(
            String url,
            String method,
            HttpBody body,
            Map<String, String> headers,
            Map<String, String> args,
            Map<String, InterpolationOutcome> outcomes) {
        super(outcomes);
        // Rejected here rather than resolved arbitrarily at lookup time.
        HttpHeaderUtils.requireNoCaseDuplicates(headers);
        this.url = url;
        this.method = method == null ? DEFAULT_METHOD : method;
        this.headers = headers == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(headers));
        this.args = args == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(args));
        this.body = body;
        if (body != null && body.getForm() != null) {
            requireFormContentType(this.headers);
        }
    }

    /**
     * {@code outcomes} defaults to {@code null} (not yet an interpolated copy). This is the
     * constructor the loader binds an authored {@code requestDefinition:} block to.
     *
     * @param url the request URL
     * @param method the HTTP method
     * @param body the request body, or {@code null}
     * @param headers the request headers
     * @param args extra arguments for the handler, or {@code null} for none
     */
    @JsonCreator
    public HttpRequestDefinition(
            String url, String method, HttpBody body, Map<String, String> headers, Map<String, String> args) {
        this(url, method, body, headers, args, null);
    }

    /**
     * A request that passes its handler no arguments, which is every request until one needs a
     * handler-specific knob.
     *
     * @param url the request URL
     * @param method the HTTP method
     * @param body the request body, or {@code null}
     * @param headers the request headers
     */
    public HttpRequestDefinition(String url, String method, HttpBody body, Map<String, String> headers) {
        this(url, method, body, headers, null, null);
    }

    /**
     * Rejects a token-free {@code Content-Type} that is not the form media type.
     *
     * @param headers the request headers, already copied, or {@code null}
     * @throws BratException if a token-free {@code Content-Type} names another media type
     */
    private static void requireFormContentType(Map<String, String> headers) {
        var declared = HttpHeaderUtils.get(headers, CONTENT_TYPE);
        if (declared == null || declared.contains(TOKEN_PREFIX)) {
            return;
        }
        if (!APPLICATION_FORM_URLENCODED.getMimeType().equals(HttpHeaderUtils.mediaType(declared))) {
            throw new BratException("A form body is sent as " + APPLICATION_FORM_URLENCODED.getMimeType()
                    + ", but the request declares the Content-Type '" + declared
                    + "'. Leave the header out, or send the body as raw");
        }
    }
}
