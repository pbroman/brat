package dev.pbroman.brat.core.interpolation.configdata;

import java.util.LinkedHashMap;

import dev.pbroman.brat.core.api.interpolation.ConfigDataInterpolator;
import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.data.RepeatUntil;
import dev.pbroman.brat.core.data.runtime.RuntimeData;

import static dev.pbroman.brat.core.interpolation.configdata.InterpolatorUtils.asStringOrNull;
import static dev.pbroman.brat.core.interpolation.configdata.InterpolatorUtils.checkNotInterpolated;
import static dev.pbroman.brat.core.interpolation.configdata.InterpolatorUtils.interpolateIfPresent;

/**
 * Interpolates a {@link RepeatUntil}'s bounds, and deliberately not its condition.
 */
public final class RepeatUntilInterpolator implements ConfigDataInterpolator<RepeatUntil> {

    /**
     * Interpolates {@code maxAttempts}, {@code waitBetweenAttempts} and {@code messageOnFail}.
     * <p>
     * <strong>The condition is carried through untouched, still un-interpolated.</strong> It is the
     * one field of this block whose value depends on the response the loop is waiting for, and the
     * bounds are resolved once, before the first attempt — so interpolating it here would resolve
     * {@code ${response.…}} against a response that does not exist yet, and against the wrong
     * response on every attempt after the first. The loop interpolates it per attempt instead, which
     * is the only moment at which it means anything.
     * <p>
     * That makes the returned copy a deliberate exception to copy-on-interpolate: an interpolated
     * {@code RepeatUntil} holds an <em>authored</em> {@code Condition}. Interpolating it repeatedly
     * is safe because each interpolation yields a fresh copy and leaves the authored one alone.
     * <p>
     * Outcome keys are the field names themselves, and only for fields that were declared.
     * <p>
     * Nothing is parsed here: {@code maxAttempts} and {@code waitBetweenAttempts} stay text, and
     * become numbers at the point of use, where a value that is not a number can be reported against
     * the request that used it.
     *
     * @param target the repeat-until block to interpolate
     * @param interpolation the interpolation implementation
     * @param runtimeData the runtime data
     * @return a new block with every declared bound interpolated and the authored condition carried
     *         through unchanged. A {@code null} {@code maxAttempts}, {@code waitBetweenAttempts} or
     *         {@code messageOnFail} yields no outcome and stays {@code null} on the copy
     * @throws dev.pbroman.brat.core.exception.BratException if {@code target} is {@code null} or is
     *         already an interpolated copy
     */
    @Override
    public RepeatUntil interpolated(RepeatUntil target, Interpolation interpolation, RuntimeData runtimeData) {
        checkNotInterpolated(target);

        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();

        var maxAttemptsOutcome =
                interpolateIfPresent(interpolation, runtimeData, outcomes, "maxAttempts", target.getMaxAttempts());
        var waitBetweenAttemptsOutcome = interpolateIfPresent(
                interpolation, runtimeData, outcomes, "waitBetweenAttempts", target.getWaitBetweenAttempts());
        var messageOnFailOutcome =
                interpolateIfPresent(interpolation, runtimeData, outcomes, "messageOnFail", target.getMessageOnFail());

        return new RepeatUntil(
                target.getCondition(),
                asStringOrNull(maxAttemptsOutcome),
                asStringOrNull(waitBetweenAttemptsOutcome),
                asStringOrNull(messageOnFailOutcome),
                outcomes);
    }
}
