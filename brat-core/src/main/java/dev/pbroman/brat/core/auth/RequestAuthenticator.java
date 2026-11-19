package dev.pbroman.brat.core.auth;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.pbroman.brat.core.api.auth.AuthHandler;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.api.reporting.ValueMask;
import dev.pbroman.brat.core.data.Auth;
import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.util.HttpHeaderUtils;
import org.apache.commons.lang3.StringUtils;

import static dev.pbroman.brat.core.data.HttpRequestDefinition.OUTCOME_HEADER_PREFIX;
import static dev.pbroman.brat.core.util.Constants.AUTHORIZATION;
import static dev.pbroman.brat.core.util.Constants.MASK;

/**
 * Applies one request's credentials: asks its auth handler for the headers to add, checks what came
 * back, and builds the request as it is sent.
 */
public final class RequestAuthenticator {

    private static final String PROXY_AUTHORIZATION = "Proxy-Authorization";

    private RequestAuthenticator() {
        // static helpers only
    }

    /**
     * Returns {@code request} with the headers {@code handler} computes for {@code auth} added, and adds
     * their credentials to {@code mask}.
     * <p>
     * <strong>The request as sent.</strong> The copy has the url, method, body and args of
     * {@code request}, its headers followed by the added ones, and is interpolated. Its outcomes are
     * every outcome of {@code request} plus one per added header, keyed {@code header.<name>} with the
     * name as the handler returned it, whose {@code value} is the header's real value. The rest depends
     * on whether {@code auth} was built from a secret — whether any of its outcomes
     * {@code containsSecret}, so an {@code auth} holding no outcomes was not:
     * <ul>
     *   <li><strong>From a secret:</strong> {@code reportingString} is {@code auth <type> → <masked>},
     *       where {@code <masked>} keeps the scheme word of an {@code Authorization} or
     *       {@code Proxy-Authorization} header (the name matched ignoring case) whose value holds a space
     *       — {@code Basic ***} — and is {@code ***} for every other header; {@code containsSecret} is
     *       {@code true}; and each header's credential is added to {@code mask} as a secret.</li>
     *   <li><strong>Otherwise</strong> — credentials typed into the suite, or captured from a response:
     *       {@code reportingString} is {@code auth <type> → <value>}, in clear; {@code containsSecret} is
     *       {@code false}; and each header's credential is added to {@code mask} as a guessed value, so a
     *       report masks it unless it is asked to show it.</li>
     * </ul>
     * A header's credential is what follows the first space of an {@code Authorization} or
     * {@code Proxy-Authorization} value holding one, and the whole value otherwise.
     * <p>
     * <strong>No headers to add</strong> returns {@code request} itself, and adds nothing to
     * {@code mask}.
     * <p>
     * {@code request} is never modified, and {@code handler} is called exactly once.
     *
     * @param auth the request's credentials, interpolated; must not be {@code null}
     * @param handler the handler registered for {@code auth}'s type; must not be {@code null}
     * @param request the interpolated request about to be sent; must not be {@code null}
     * @param mask the run's mask, which the added credentials are recorded in; must not be {@code null}
     * @return the request to send; never {@code null}
     * @throws BratException if the handler returns {@code null}; if a returned header name or value is
     *         {@code null} or blank; if a returned header is one {@code request} already carries,
     *         compared ignoring case — the message names that header and points at
     *         {@code auth: {type: none}} as the way to send the authored one instead; or if two returned
     *         names differ only in case. Every message names the auth type and none quotes a header value.
     *         Whatever {@code handler} itself throws propagates unchanged
     */
    public static HttpRequestDefinition authenticated(
            Auth auth, AuthHandler handler, HttpRequestDefinition request, ValueMask mask) {
        var type = auth.getType();
        var added = handler.apply(auth, request);
        if (added == null) {
            throw new BratException(String.format("The '%s' auth handler returned no headers map", type));
        }
        if (added.isEmpty()) {
            return request;
        }
        checkReturned(type, added, request.getHeaders());

        var fromSecret = auth.isInterpolated()
                && auth.getOutcomes().values().stream().anyMatch(InterpolationOutcome::containsSecret);
        var headers = new LinkedHashMap<String, String>();
        if (request.getHeaders() != null) {
            headers.putAll(request.getHeaders());
        }
        var outcomes = new LinkedHashMap<>(request.getOutcomes());
        for (var header : added.entrySet()) {
            var name = header.getKey();
            var value = header.getValue();
            headers.put(name, value);
            var shown = fromSecret ? masked(name, value) : value;
            outcomes.put(
                    OUTCOME_HEADER_PREFIX + name,
                    new InterpolationOutcome(value, "auth " + type + " → " + shown, fromSecret));
            if (fromSecret) {
                mask.addSecret(credential(name, value));
            } else {
                mask.addGuessed(credential(name, value));
            }
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
     * The credential in an added header: what follows the first space of an {@code Authorization} or
     * {@code Proxy-Authorization} value holding one, the whole value otherwise.
     *
     * @param name the header name
     * @param value the header value, not blank
     * @return the credential
     */
    private static String credential(String name, String value) {
        int space = value.indexOf(' ');
        return carriesScheme(name) && space > 0 ? value.substring(space + 1) : value;
    }

    private static boolean carriesScheme(String name) {
        return AUTHORIZATION.equalsIgnoreCase(name) || PROXY_AUTHORIZATION.equalsIgnoreCase(name);
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
        int space = value.indexOf(' ');
        return carriesScheme(name) && space > 0 ? value.substring(0, space) + " " + MASK : MASK;
    }
}
