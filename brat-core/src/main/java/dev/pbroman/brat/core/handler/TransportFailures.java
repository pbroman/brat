package dev.pbroman.brat.core.handler;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.regex.Pattern;

import javax.net.ssl.SSLException;

import dev.pbroman.brat.core.rendering.ReportText;
import org.apache.hc.client5.http.ConnectTimeoutException;
import org.apache.hc.core5.http.ConnectionRequestTimeoutException;
import org.apache.hc.core5.http.NoHttpResponseException;

/**
 * Static helpers turning a transport failure — a request that produced no response — into the message
 * a suite author reads.
 * <p>
 * The message names what went wrong in the author's terms rather than the client library's, because
 * each cause asks for a different fix: a refused connection means nothing is listening, an unknown host
 * a misspelt url, a timeout a slow or hanging server, a TLS failure a certificate.
 */
final class TransportFailures {

    private TransportFailures() {}

    /**
     * Describes why a request produced no response.
     * <p>
     * {@code <server>} below is the url's host and its port, the scheme's default port where the url
     * names none — {@code localhost:8080}, {@code api.example.com:443}. {@code <url>} is the url as
     * given, without its user info. <strong>User info in the url is never part of the message</strong>,
     * so a password written into it cannot reach a report this way. A duration reads as the console prints one:
     * {@code 500 ms}, {@code 30.0 s}. Checked in this order, the first match deciding:
     * <ul>
     *   <li><strong>connecting timed out</strong> ({@code org.apache.hc.client5.http.ConnectTimeoutException})
     *       — {@code Could not connect to <server> within <connectTimeoutMs> (the connect timeout)}</li>
     *   <li><strong>no free pooled connection in time</strong>
     *       ({@code org.apache.hc.core5.http.ConnectionRequestTimeoutException}) —
     *       {@code No connection to <server> became free within <timeoutMs> (the request's timeout)}</li>
     *   <li><strong>no answer in time</strong> (any other {@link java.net.SocketTimeoutException}) —
     *       {@code No response from <url> within <timeoutMs> (the request's timeout)}</li>
     *   <li><strong>refused</strong> (a {@link java.net.ConnectException}) —
     *       {@code Could not connect to <server> — connection refused}</li>
     *   <li><strong>unknown host</strong> (an {@link java.net.UnknownHostException}) —
     *       {@code Could not connect to <server> — unknown host <host>}</li>
     *   <li><strong>TLS</strong> (an {@link javax.net.ssl.SSLException}) —
     *       {@code TLS with <server> failed: <the exception's message>}, or, when it has none, the
     *       innermost cause named as below</li>
     *   <li><strong>closed without an answer</strong> ({@code org.apache.hc.core5.http.NoHttpResponseException})
     *       — {@code <server> closed the connection without a response}</li>
     *   <li><strong>anything else</strong> — {@code Could not perform the request to <url> — <simple class
     *       name>: <message>} of the <em>innermost</em> cause, since the client wraps what went wrong
     *       (a redirect loop arrives as a {@code ClientProtocolException} around a
     *       {@code RedirectException}); without {@code : <message>} when that cause has none</li>
     * </ul>
     * Only {@code failure} itself is matched against the categories above, never its causes.
     *
     * @param failure what the client threw; must not be {@code null}
     * @param uri the request's url, already parsed; must not be {@code null}
     * @param timeoutMs the request's timeout, in milliseconds
     * @param connectTimeoutMs the handler's connect timeout, in milliseconds
     * @return the message; never {@code null}
     */
    static String describe(IOException failure, URI uri, long timeoutMs, long connectTimeoutMs) {
        var server = serverOf(uri);
        var url = withoutUserInfo(uri);
        return switch (failure) {
            case ConnectTimeoutException e ->
                "Could not connect to " + server + " within " + ReportText.duration(connectTimeoutMs)
                        + " (the connect timeout)";
            case ConnectionRequestTimeoutException e ->
                "No connection to " + server + " became free within " + ReportText.duration(timeoutMs)
                        + " (the request's timeout)";
            case SocketTimeoutException e ->
                "No response from " + url + " within " + ReportText.duration(timeoutMs) + " (the request's timeout)";
            case ConnectException e -> "Could not connect to " + server + " — connection refused";
            case UnknownHostException e -> "Could not connect to " + server + " — unknown host " + uri.getHost();
            case SSLException e ->
                "TLS with " + server + " failed: " + (e.getMessage() == null ? named(innermost(e)) : e.getMessage());
            case NoHttpResponseException e -> server + " closed the connection without a response";
            default -> "Could not perform the request to " + url + " — " + named(innermost(failure));
        };
    }

    /** The url's host and port, the scheme's default port filled in; never its user info. */
    private static String serverOf(URI uri) {
        var port = uri.getPort();
        if (port < 0) {
            port = "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
        }
        return uri.getHost() + ":" + port;
    }

    /** The url as text, its user info left out; everything else exactly as given. */
    private static String withoutUserInfo(URI uri) {
        var text = uri.toString();
        var userInfo = uri.getRawUserInfo();
        return userInfo == null ? text : text.replaceFirst(Pattern.quote(userInfo + "@"), "");
    }

    /** The last cause in the chain, stopping before a cause already seen, so a cycle ends. */
    private static Throwable innermost(Throwable failure) {
        var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        var current = failure;
        seen.add(current);
        while (current.getCause() != null && seen.add(current.getCause())) {
            current = current.getCause();
        }
        return current;
    }

    private static String named(Throwable cause) {
        var name = cause.getClass().getSimpleName();
        return cause.getMessage() == null ? name : name + ": " + cause.getMessage();
    }
}
