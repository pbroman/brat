package dev.pbroman.brat.core.data;

import java.util.Map;

import dev.pbroman.brat.core.api.data.RequestDefinition;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.util.Constants;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import static dev.pbroman.brat.core.util.Constants.DEFAULT_TIMEOUT_MS;

/**
 * What the run decided about one request, as opposed to what the author wrote in it.
 * <p>
 * The suite tree's orchestration metadata, resolved for a single request: declared on
 * {@link TestSuite} and {@link Request}, cascaded by the walk, and handed to the request handler
 * beside the {@link RequestDefinition} it executes. The definition is
 * the <em>payload</em> — what goes on the wire — while this is everything the run decided
 * <em>about</em> sending it.
 * <p>
 * Not authored directly and not bound by the loader: there is no {@code requestOptions:} block, and
 * the walk assembles one per request from fields spread across the ancestors. A plugin's own
 * per-request setting belongs in the handler's {@code args} bag instead.
 */
@Getter
public final class RequestOptions extends ConfigData {

    /** The word a suite writes to ask for the default timeout explicitly, matched ignoring case. */
    public static final String DEFAULT_TIMEOUT_KEYWORD = "default";

    /**
     * How long to wait for the request, in milliseconds and in text form; {@value #DEFAULT_TIMEOUT_KEYWORD} or
     * blank for {@link Constants#DEFAULT_TIMEOUT_MS}; or {@code null} when
     * nothing declared one, which reads as the default too. Text rather than a number
     * because it may hold {@code ${...}} tokens until this block is interpolated; read it through
     * {@link #timeoutMs()} to get the defaulted, validated value.
     */
    private final String timeout;

    /**
     * The credentials this request is sent with, or {@code null} when nothing above it declared any.
     * Already applied by the time a request handler receives these options: a handler sends the
     * request it is given and never applies this again.
     */
    private final Auth auth;

    /**
     * Constructs an interpolated copy of a request's options, carrying its named outcomes.
     *
     * @param timeout the resolved timeout in milliseconds, in text form, or {@code null} for the
     *        default
     * @param auth the interpolated credentials, or {@code null} for none
     * @param outcomes the named interpolation outcomes of an interpolated copy, or {@code null} on an
     *        as-assembled instance
     */
    public RequestOptions(String timeout, Auth auth, Map<String, InterpolationOutcome> outcomes) {
        super(outcomes);
        this.timeout = timeout;
        this.auth = auth;
    }

    /**
     * {@code outcomes} defaults to {@code null} (not yet an interpolated copy). This is the
     * constructor the walk assembles an authored request's options with.
     *
     * @param timeout the timeout in milliseconds, in text form, possibly holding {@code ${...}}
     *        tokens
     * @param auth the credentials the walk resolved for the request, as authored, or {@code null} for
     *        none
     */
    public RequestOptions(String timeout, Auth auth) {
        this(timeout, auth, null);
    }

    /**
     * Options with a timeout and no credentials; {@code auth} and {@code outcomes} default to
     * {@code null}.
     *
     * @param timeout the timeout in milliseconds, in text form, possibly holding {@code ${...}}
     *        tokens
     */
    public RequestOptions(String timeout) {
        this(timeout, null, null);
    }

    /**
     * The timeout as a positive number of milliseconds, defaulted when none was declared.
     * <p>
     * A {@code null} or blank {@code timeout}, or {@value #DEFAULT_TIMEOUT_KEYWORD} in any case, yields
     * {@link Constants#DEFAULT_TIMEOUT_MS}; surrounding whitespace is
     * ignored throughout. On an interpolated copy this never throws, since interpolation has already validated
     * the value; on an as-assembled instance whose {@code timeout} still holds a {@code ${...}} token
     * it throws, because the token is not a number.
     *
     * @return the timeout in milliseconds; always positive
     * @throws BratException if {@code timeout} is neither blank, {@value #DEFAULT_TIMEOUT_KEYWORD} nor a whole
     *         number, quoting the value
     * @throws BratException if {@code timeout} is zero or negative, which would describe a request
     *         that cannot succeed
     */
    public long timeoutMs() {
        if (StringUtils.isBlank(timeout) || DEFAULT_TIMEOUT_KEYWORD.equalsIgnoreCase(timeout.trim())) {
            return Long.parseLong(DEFAULT_TIMEOUT_MS);
        }
        try {
            var timeoutMs = Long.parseLong(timeout.trim());
            if (timeoutMs <= 0) {
                throw new BratException(
                        String.format("The timeout '%s' must be a positive number of milliseconds", timeout));
            }
            return timeoutMs;
        } catch (NumberFormatException e) {
            throw new BratException(
                    String.format("The timeout '%s' is not a whole number of milliseconds", timeout), e);
        }
    }
}
