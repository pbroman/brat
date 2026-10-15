package dev.pbroman.brat.core.auth;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.pbroman.brat.core.api.auth.AuthHandler;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.data.Auth;
import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.util.HttpHeaderUtils;
import org.apache.commons.lang3.StringUtils;

import static dev.pbroman.brat.core.util.Constants.AUTHORIZATION;

/**
 * Applies one request's credentials: asks its auth handler for the headers to add, checks what came
 * back, and builds the request as it is sent.
 */
public final class RequestAuthenticator {

    private static final String PROXY_AUTHORIZATION = "Proxy-Authorization";
    private static final String MASK = "***";

    private RequestAuthenticator() {
        // static helpers only
    }

    /**
     * Returns {@code request} with the headers {@code handler} computes for {@code auth} added.
     * <p>
     * <strong>The request as sent.</strong> The copy has the url, method, body and args of
     * {@code request}, its headers followed by the added ones, and is interpolated. Its outcomes are
     * every outcome of {@code request} plus one per added header, keyed {@code header.<name>} with the
     * name as the handler returned it:
     * <ul>
     *   <li>{@code value} is the header's real value;</li>
     *   <li>{@code reportingString} is {@code auth <type> → <masked>}, where {@code <masked>} keeps the
     *       scheme word of an {@code Authorization} or {@code Proxy-Authorization} header (the name
     *       matched ignoring case) whose value holds a space — {@code Basic ***} — and is {@code ***}
     *       for every other header;</li>
     *   <li>{@code containsSecret} is {@code true}.</li>
     * </ul>
     * <strong>No headers to add</strong> returns {@code request} itself.
     * <p>
     * {@code request} is never modified, and {@code handler} is called exactly once.
     *
     * @param auth the request's credentials, interpolated; must not be {@code null}
     * @param handler the handler registered for {@code auth}'s type; must not be {@code null}
     * @param request the interpolated request about to be sent; must not be {@code null}
     * @return the request to send; never {@code null}
     * @throws BratException if the handler returns {@code null}; if a returned header name or value is
     *         {@code null} or blank; if a returned header is one {@code request} already carries,
     *         compared ignoring case — the message names that header and points at
     *         {@code auth: {type: none}} as the way to send the authored one instead; or if two returned
     *         names differ only in case. Every message names the auth type and none quotes a header value.
     *         Whatever {@code handler} itself throws propagates unchanged
     */
    public static HttpRequestDefinition authenticated(Auth auth, AuthHandler handler, HttpRequestDefinition request) {
        var type = auth.getType();
        var added = handler.apply(auth, request);
        if (added == null) {
            throw new BratException(String.format("The '%s' auth handler returned no headers map", type));
        }
        if (added.isEmpty()) {
            return request;
        }
        checkReturned(type, added, request.getHeaders());

        var headers = new LinkedHashMap<String, String>();
        if (request.getHeaders() != null) {
            headers.putAll(request.getHeaders());
        }
        var outcomes = new LinkedHashMap<>(request.getOutcomes());
        for (var header : added.entrySet()) {
            headers.put(header.getKey(), header.getValue());
            var reportingString = "auth " + type + " → " + masked(header.getKey(), header.getValue());
            outcomes.put(
                    "header." + header.getKey(), new InterpolationOutcome(header.getValue(), reportingString, true));
        }
        return new HttpRequestDefinition(
                request.getUrl(), request.getMethod(), request.getBody(), headers, request.getArgs(), outcomes);
    }

    /**
     * Rejects returned headers that cannot be sent: a blank name or value, two names differing only in
     * case, or a name the request already carries. No message quotes a value.
     *
     * @param type the auth type, for the messages
     * @param added the headers the handler returned
     * @param authored the request's own headers, or {@code null}
     * @throws BratException for the first header that cannot be sent
     */
    private static void checkReturned(String type, Map<String, String> added, Map<String, String> authored) {
        for (var header : added.entrySet()) {
            if (StringUtils.isBlank(header.getKey())) {
                throw new BratException(
                        String.format("The '%s' auth handler returned a header with a blank name", type));
            }
            if (StringUtils.isBlank(header.getValue())) {
                throw new BratException(String.format(
                        "The '%s' auth handler returned the header '%s' with a blank value", type, header.getKey()));
            }
        }
        try {
            HttpHeaderUtils.requireNoCaseDuplicates(added);
        } catch (BratException e) {
            throw new BratException(
                    String.format("The '%s' auth handler returned two headers: %s", type, e.getMessage()), e);
        }
        for (var name : added.keySet()) {
            if (HttpHeaderUtils.contains(authored, name)) {
                throw new BratException(String.format(
                        "The request already carries the header '%s', which the '%s' auth sets. To send the"
                                + " request's own header instead, declare auth: {type: none} on it",
                        name, type));
            }
        }
    }

    /**
     * How an added header reads in a report: the scheme word of an {@code Authorization} or
     * {@code Proxy-Authorization} value kept, everything else masked.
     *
     * @param name the header name
     * @param value the header value, not blank
     * @return the masked value
     */
    private static String masked(String name, String value) {
        boolean carriesScheme = AUTHORIZATION.equalsIgnoreCase(name) || PROXY_AUTHORIZATION.equalsIgnoreCase(name);
        int space = value.indexOf(' ');
        return carriesScheme && space > 0 ? value.substring(0, space) + " " + MASK : MASK;
    }
}
