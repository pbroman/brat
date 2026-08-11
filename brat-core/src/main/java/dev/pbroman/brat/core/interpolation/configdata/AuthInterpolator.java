package dev.pbroman.brat.core.interpolation.configdata;

import java.util.LinkedHashMap;

import dev.pbroman.brat.core.api.interpolation.ConfigDataInterpolator;
import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.data.Auth;
import dev.pbroman.brat.core.data.runtime.RuntimeData;

import static dev.pbroman.brat.core.interpolation.configdata.InterpolatorUtils.asStringOrNull;
import static dev.pbroman.brat.core.interpolation.configdata.InterpolatorUtils.checkNotInterpolated;
import static dev.pbroman.brat.core.interpolation.configdata.InterpolatorUtils.interpolateIfPresent;

/**
 * Interpolates every field of an {@link Auth}.
 */
public final class AuthInterpolator implements ConfigDataInterpolator<Auth> {

    /**
     * Interpolates the auth block's {@code type}, {@code username}, {@code password} and
     * {@code token}.
     * <p>
     * Outcome keys are the field names themselves. {@code type} is always interpolated and always
     * recorded — an auth block without one is not a legal auth block — while the other three are
     * optional and contribute nothing when absent.
     *
     * @param target the auth block to interpolate
     * @param interpolation the interpolation implementation
     * @param runtimeData the runtime data
     * @return a new auth block with every declared field interpolated, carrying one outcome per
     *         interpolated field in field-declaration order. A {@code null} {@code username},
     *         {@code password} or {@code token} yields no outcome for that field and stays
     *         {@code null} on the copy
     * @throws dev.pbroman.brat.core.exception.BratException if {@code target} is already an
     *         interpolated copy
     */
    @Override
    public Auth interpolated(Auth target, Interpolation interpolation, RuntimeData runtimeData) {
        checkNotInterpolated(target);

        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();
        var typeOutcome = interpolation.outcome(target.getType(), runtimeData);
        outcomes.put("type", typeOutcome);

        var usernameOutcome =
                interpolateIfPresent(interpolation, runtimeData, outcomes, "username", target.getUsername());
        var passwordOutcome =
                interpolateIfPresent(interpolation, runtimeData, outcomes, "password", target.getPassword());
        var tokenOutcome = interpolateIfPresent(interpolation, runtimeData, outcomes, "token", target.getToken());

        return new Auth(
                typeOutcome.asString(),
                asStringOrNull(usernameOutcome),
                asStringOrNull(passwordOutcome),
                asStringOrNull(tokenOutcome),
                outcomes);
    }
}
