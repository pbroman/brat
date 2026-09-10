package dev.pbroman.brat.core.data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import dev.pbroman.brat.core.api.data.RequestDefinition;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.util.HttpHeaderUtils;
import lombok.Getter;
import org.apache.commons.lang3.Strings;

import static dev.pbroman.brat.core.util.Constants.BODY_STRING;
import static dev.pbroman.brat.core.util.Constants.DEFAULT_METHOD;
import static dev.pbroman.brat.core.util.Constants.FILE_BODY;
import static dev.pbroman.brat.core.util.Constants.RAW_BODY;
import static org.apache.hc.core5.http.ContentType.APPLICATION_FORM_URLENCODED;
import static org.apache.hc.core5.http.HttpHeaders.CONTENT_TYPE;

/**
 * The default, HTTP-based {@link RequestDefinition} implementation.
 * <p>
 * {@code final}: a plugin adding a protocol writes its own {@code RequestDefinition} implementation
 * beside this one rather than extending it, and "HTTP plus one extra knob" is the per-request handler
 * {@code args} bag rather than a subclass.
 */
@Getter
public final class HttpRequestDefinition extends ConfigData implements RequestDefinition {

    private final String url;
    private final String method;
    private final String timeout;
    private final Map<String, String> body;
    private final Map<String, String> headers;
    private final Auth auth;

    /**
     * Constructs a request definition, deriving the body's {@code _bodyString} form where it can be
     * derived without I/O.
     * <p>
     * {@code body} is not mutated: the instance holds a copy carrying any derived
     * {@code _bodyString} entry. A {@code file:} body is <em>not</em> read here — the read happens at
     * request time, after interpolation, so that both the path and the file's content may contain
     * {@code ${...}} tokens.
     *
     * @param url the request URL, possibly holding {@code ${...}} tokens
     * @param method the HTTP method, or {@code null} for {@code GET} — the convention every
     *        comparable tool follows, and better than binding a null that fails at request time
     * @param timeout the request timeout in milliseconds, or {@code null} for the default
     * @param body the body, keyed by one of the well-known body keys, or {@code null}
     * @param headers the request headers, or {@code null}
     * @param auth the authentication to apply, or {@code null}
     * @param outcomes the interpolation outcomes of an interpolated copy, or {@code null} on an
     *        as-authored instance
     * @throws dev.pbroman.brat.core.exception.BratException if two header names differ only in case.
     *         They are one header to HTTP and two keys to YAML, so declaring both states two values
     *         for one header — rejected here rather than resolved arbitrarily at lookup time, so that
     *         it fails whether or not anything reads that particular header
     */
    public HttpRequestDefinition(
            String url,
            String method,
            String timeout,
            Map<String, String> body,
            Map<String, String> headers,
            Auth auth,
            Map<String, InterpolationOutcome> outcomes) {
        super(outcomes);
        HttpHeaderUtils.requireNoCaseDuplicates(headers);
        this.url = url;
        this.method = method == null ? DEFAULT_METHOD : method;
        this.timeout = timeout;
        this.headers = headers == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(headers));
        this.auth = auth;
        this.body = prepared(body, this.headers);
    }

    /**
     * {@code outcomes} defaults to {@code null} (not yet an interpolated copy). This is the
     * constructor the loader binds an authored {@code requestDefinition:} block to.
     *
     * @param url the request URL
     * @param method the HTTP method
     * @param timeout the request timeout
     * @param body the request body entries
     * @param headers the request headers
     * @param auth the auth configuration
     */
    @JsonCreator
    public HttpRequestDefinition(
            String url,
            String method,
            String timeout,
            Map<String, String> body,
            Map<String, String> headers,
            Auth auth) {
        this(url, method, timeout, body, headers, auth, null);
    }

    /**
     * Returns the body with its {@code _bodyString} entry derived, where deriving it needs no I/O.
     * <p>
     * Three cases, in order: a {@code raw} body becomes {@code _bodyString} verbatim; otherwise, if
     * the request's {@code Content-Type} is form-encoded (matched case-insensitively, since a suite
     * may spell the header any way) and no {@code _bodyString} is present yet, the remaining entries
     * are joined as {@code k=v&k=v}; otherwise the body is returned unchanged. A {@code file} body
     * falls into the third case and is resolved at request time.
     *
     * @param body the authored body, or {@code null}
     * @param headers the request headers, already copied, or {@code null}
     * @return an unmodifiable copy of {@code body} with any derivable {@code _bodyString} added, or
     *         {@code null} if {@code body} is {@code null}. Never throws for an absent or
     *         differently-spelled {@code Content-Type}, nor for {@code null} headers
     */
    private static Map<String, String> prepared(Map<String, String> body, Map<String, String> headers) {
        if (body == null) {
            return null;
        }
        var prepared = new LinkedHashMap<>(body);
        if (body.get(RAW_BODY) != null) {
            prepared.put(BODY_STRING, body.get(RAW_BODY));
        } else if (body.get(FILE_BODY) == null
                && body.get(BODY_STRING) == null
                && !body.isEmpty()
                && isFormUrlEncoded(headers)) {
            prepared.put(BODY_STRING, formEncoded(body));
        }
        return Collections.unmodifiableMap(prepared);
    }

    /**
     * Whether the request declares a form-encoded content type.
     *
     * @param headers the request headers, or {@code null}
     * @return {@code true} only if a {@code Content-Type} header is present under any capitalisation
     *         and its value starts with the form-urlencoded mime type; {@code false} for {@code null}
     *         headers, an absent header, or a {@code null} value
     */
    private static boolean isFormUrlEncoded(Map<String, String> headers) {
        // Strings.CS rather than the deprecated StringUtils.startsWith, and CS rather than CI to keep
        // the behaviour this method already had. Whether the *value* should match case-insensitively
        // is a separate question - media types are case-insensitive per RFC 9110 - and changing it
        // here would smuggle a behaviour change into a deprecation fix.
        return Strings.CS.startsWith(
                HttpHeaderUtils.get(headers, CONTENT_TYPE), APPLICATION_FORM_URLENCODED.getMimeType());
    }

    /**
     * Joins the body's entries into an {@code application/x-www-form-urlencoded} payload.
     *
     * @param body the body entries, none of which is a well-known body key at this point
     * @return the entries joined as {@code k=v&k=v}, in the body's iteration order
     */
    private static String formEncoded(Map<String, String> body) {
        return body.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
    }
}
