package dev.pbroman.brat.core.handler;

import dev.pbroman.brat.core.auth.NoneAuthHandler;
import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.data.RequestOptions;
import dev.pbroman.brat.core.reporting.SensitiveValues;
import org.apache.hc.client5.http.impl.DefaultRedirectStrategy;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.protocol.HttpContext;

import static dev.pbroman.brat.core.util.Constants.AUTHORIZATION;

/**
 * The redirect rule of {@link ApacheHttpRequestHandler}: a redirect to another origin is refused when
 * the request carries credentials, so they never reach a host the server named.
 * <p>
 * Which redirects are followed at all — the status codes, the {@code Location} — is
 * {@link DefaultRedirectStrategy}'s; this decides only whether a redirect it found may be followed.
 */
final class CredentialAwareRedirectStrategy extends DefaultRedirectStrategy {

    /**
     * The request context attribute saying whether the request carries credentials, a
     * {@link Boolean} set by the handler before the request is sent. Absent reads as {@code false}.
     */
    static final String CARRIES_CREDENTIALS = "brat.carriesCredentials";

    private static final String COOKIE = "Cookie";
    private static final String HTTPS = "https";
    private static final int HTTP_PORT = 80;
    private static final int HTTPS_PORT = 443;

    /**
     * Whether {@code definition}, sent with {@code options}, carries credentials.
     * <p>
     * Credentials are anything a redirect could carry to another origin — a 307 or 308 re-sends the body
     * as well as the headers:
     * <ul>
     *   <li>an {@code Authorization} or {@code Cookie} header, the name matched ignoring case;</li>
     *   <li>an {@code options} auth whose type is not {@code none};</li>
     *   <li>any value built from a secret — an outcome of {@code definition} that
     *       {@code containsSecret}, whether on the url, a header or the body;</li>
     *   <li>a form entry whose name is a sensitive one ({@link SensitiveValues#isSensitiveName}),
     *       typed in clear included.</li>
     * </ul>
     *
     * @param definition the interpolated request; must not be {@code null}
     * @param options its interpolated options; must not be {@code null}
     * @return whether a redirect to another origin must be refused
     */
    static boolean carriesCredentials(HttpRequestDefinition definition, RequestOptions options) {
        var headers = definition.getHeaders();
        if (headers != null) {
            for (var name : headers.keySet()) {
                if (AUTHORIZATION.equalsIgnoreCase(name) || COOKIE.equalsIgnoreCase(name)) {
                    return true;
                }
            }
        }
        var auth = options.getAuth();
        if (auth != null && !NoneAuthHandler.TYPE.equals(auth.getType())) {
            return true;
        }
        if (definition.isInterpolated()) {
            for (var outcome : definition.getOutcomes().values()) {
                if (outcome.containsSecret()) {
                    return true;
                }
            }
        }
        var body = definition.getBody();
        if (body != null && body.getForm() != null) {
            for (var name : body.getForm().keySet()) {
                if (SensitiveValues.isSensitiveName(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Allows a redirect unless it goes to another origin while the request carries credentials.
     * <p>
     * Two targets are the same origin when their scheme, host name (ignoring case) and port are equal,
     * a missing port reading as its scheme's default ({@code 80} for {@code http}, {@code 443} for
     * {@code https}). So another port, or {@code http} to {@code https}, is another origin; host names
     * are compared as written, so {@code localhost} and {@code 127.0.0.1} differ. Whether the request
     * carries credentials is the context's {@link #CARRIES_CREDENTIALS} attribute.
     *
     * @param currentTarget where the request was sent
     * @param newTarget where the redirect points
     * @param redirect the request about to be sent to {@code newTarget}
     * @param context the request's context
     * @return {@code false} for another origin when the request carries credentials, {@code true}
     *         otherwise
     */
    @Override
    public boolean isRedirectAllowed(
            HttpHost currentTarget, HttpHost newTarget, HttpRequest redirect, HttpContext context) {
        var carriesCredentials = Boolean.TRUE.equals(context.getAttribute(CARRIES_CREDENTIALS));
        return !carriesCredentials || sameOrigin(currentTarget, newTarget);
    }

    /**
     * Whether two targets are one origin: scheme and host name equal ignoring case, and the same port,
     * a missing one read as its scheme's default.
     *
     * @param one a target
     * @param other another target
     * @return whether they are the same origin
     */
    private static boolean sameOrigin(HttpHost one, HttpHost other) {
        return one.getSchemeName().equalsIgnoreCase(other.getSchemeName())
                && one.getHostName().equalsIgnoreCase(other.getHostName())
                && portOf(one) == portOf(other);
    }

    private static int portOf(HttpHost host) {
        if (host.getPort() >= 0) {
            return host.getPort();
        }
        return HTTPS.equalsIgnoreCase(host.getSchemeName()) ? HTTPS_PORT : HTTP_PORT;
    }
}
