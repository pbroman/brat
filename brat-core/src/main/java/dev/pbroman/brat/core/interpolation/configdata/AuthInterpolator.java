package dev.pbroman.brat.core.interpolation.configdata;

import java.util.LinkedHashMap;

import dev.pbroman.brat.core.api.interpolation.ConfigDataInterpolator;
import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.data.Auth;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.util.Require;

import static dev.pbroman.brat.core.interpolation.configdata.InterpolatorUtils.checkNotInterpolated;
import static dev.pbroman.brat.core.interpolation.configdata.InterpolatorUtils.interpolateArgs;

/**
 * Interpolates an {@link Auth}: its {@code type} and every value in its {@code args}.
 */
public final class AuthInterpolator implements ConfigDataInterpolator<Auth> {

    /** What an {@code args} value reads as in every report. */
    static final String MASK = "***";

    /**
     * Interpolates the auth block's {@code type} and each of its {@code args}.
     * <p>
     * Outcome keys are {@code type}, then {@code args.<name>} per argument in declaration order.
     * <strong>Every argument is secret by meaning</strong>, whatever it was written as: its outcome is
     * marked as containing a secret and its {@code reportingString} is {@code ***}, whole — not
     * {@code <authored> → ***}, because the authored text may itself be a literal credential. Its
     * {@code value} holds the resolved argument, as a secret outcome's always does. {@code type} is
     * recorded as the interpolation produced it, and stays readable.
     *
     * @param target the auth block to interpolate
     * @param interpolation the interpolation implementation
     * @param runtimeData the runtime data
     * @return a new auth block with {@code type} and every argument interpolated, carrying the outcomes
     *         above. An empty {@code args} stays empty and contributes no outcome
     * @throws BratException if {@code target} is {@code null} or is
     *         already an interpolated copy, if its {@code type} is {@code null}, or if an argument
     *         has a {@code null} value — a key written with nothing after it. The message names the
     *         argument, never a value
     */
    @Override
    public Auth interpolated(Auth target, Interpolation interpolation, RuntimeData runtimeData) {
        checkNotInterpolated(target);
        Require.nonNull(target.getType(), "An auth block needs a type");

        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();
        var typeOutcome = interpolation.outcome(target.getType(), runtimeData);
        outcomes.put("type", typeOutcome);

        var argOutcomes = new LinkedHashMap<String, InterpolationOutcome>();
        var args = interpolateArgs(target.getArgs(), interpolation, runtimeData, argOutcomes);
        for (var argOutcome : argOutcomes.entrySet()) {
            outcomes.put(
                    argOutcome.getKey(),
                    new InterpolationOutcome(argOutcome.getValue().value(), MASK, true));
        }

        return new Auth(typeOutcome.asString(), args, outcomes);
    }
}
