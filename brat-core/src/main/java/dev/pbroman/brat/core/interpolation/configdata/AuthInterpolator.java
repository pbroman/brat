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
