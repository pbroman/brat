package dev.pbroman.brat.core.interpolation.configdata;

import java.util.LinkedHashMap;

import dev.pbroman.brat.core.api.interpolation.ConfigDataInterpolator;
import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.data.Auth;
import dev.pbroman.brat.core.data.RequestOptions;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import static dev.pbroman.brat.core.interpolation.configdata.InterpolatorUtils.asStringOrNull;
import static dev.pbroman.brat.core.interpolation.configdata.InterpolatorUtils.checkNotInterpolated;
import static dev.pbroman.brat.core.interpolation.configdata.InterpolatorUtils.interpolateIfPresent;

/**
 * Interpolates a request's resolved {@link RequestOptions}.
 * <p>
 * Its target is assembled by the walk rather than bound from a document, but the values in it were
 * authored, so its substitutions are recorded as outcomes like any other block's.
 */
@Slf4j
public final class RequestOptionsInterpolator implements ConfigDataInterpolator<RequestOptions> {

    private static final String AUTH_PREFIX = "auth.";

    private final AuthInterpolator authInterpolator = new AuthInterpolator();

    /**
     * Interpolates {@code timeout} and {@code auth}, and validates the timeout.
     * <p>
     * Outcome keys are {@code timeout}, unprefixed and recorded only when a timeout was declared, then
     * the auth block's own outcomes prefixed with {@code auth.} ({@code auth.type},
     * {@code auth.args.<name>}), recorded only when an {@code auth} is present — see
     * {@link AuthInterpolator#interpolated} for those, every argument among them masked.
     * The copy is validated with {@link RequestOptions#timeoutMs()} before it is returned, so
     * {@link RequestOptions#timeoutMs()} never throws on a copy this method produced.
     * <p>
     * A declared {@code timeout} that resolves to blank is accepted and reads as the default, and a
     * WARN names the authored value: blank is as often an empty variable as a decision, and
     * {@value RequestOptions#DEFAULT_TIMEOUT_KEYWORD} is the way to ask for the default on purpose.
     *
     * @param target the options to interpolate
     * @param interpolation the interpolation implementation
     * @param runtimeData the runtime data
     * @return a new instance with {@code timeout} and {@code auth} interpolated, carrying their
     *         outcomes. A {@code null} {@code timeout} yields no outcome and stays {@code null}; the
     *         default is applied by {@link RequestOptions#timeoutMs()}. A {@code null} {@code auth}
     *         yields no outcome and stays {@code null}; otherwise the copy holds an interpolated
     *         {@link Auth}
     * @throws BratException if {@code target} is {@code null} or is already an interpolated copy
     * @throws BratException if {@code timeout} interpolates to something other than blank,
     *         {@value RequestOptions#DEFAULT_TIMEOUT_KEYWORD} or a positive whole number of milliseconds
     * @throws BratException if {@code auth} cannot be interpolated, under the conditions of
     *         {@link AuthInterpolator#interpolated}
     */
    @Override
    public RequestOptions interpolated(RequestOptions target, Interpolation interpolation, RuntimeData runtimeData) {
        checkNotInterpolated(target);
        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();
        var timeoutOutcome = interpolateIfPresent(interpolation, runtimeData, outcomes, "timeout", target.getTimeout());
        var auth = target.getAuth() == null
                ? null
                : authInterpolator.interpolated(target.getAuth(), interpolation, runtimeData);
        if (auth != null) {
            for (var authOutcome : auth.getOutcomes().entrySet()) {
                outcomes.put(AUTH_PREFIX + authOutcome.getKey(), authOutcome.getValue());
            }
        }
        var interpolated = new RequestOptions(asStringOrNull(timeoutOutcome), auth, outcomes);
        if (timeoutOutcome != null && StringUtils.isBlank(interpolated.getTimeout())) {
            log.warn(
                    "The timeout '{}' resolved to blank, so the default applies; write '{}' to ask for the default"
                            + " on purpose",
                    target.getTimeout(),
                    RequestOptions.DEFAULT_TIMEOUT_KEYWORD);
        }
        // validates the interpolation
        interpolated.timeoutMs();
        return interpolated;
    }
}
