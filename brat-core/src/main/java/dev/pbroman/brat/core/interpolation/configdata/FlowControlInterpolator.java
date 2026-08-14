package dev.pbroman.brat.core.interpolation.configdata;

import java.util.LinkedHashMap;

import dev.pbroman.brat.core.api.interpolation.ConfigDataInterpolator;
import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.data.FlowControl;
import dev.pbroman.brat.core.data.RepeatUntil;
import dev.pbroman.brat.core.data.runtime.RuntimeData;

import static dev.pbroman.brat.core.interpolation.configdata.InterpolatorUtils.asStringOrNull;
import static dev.pbroman.brat.core.interpolation.configdata.InterpolatorUtils.checkNotInterpolated;
import static dev.pbroman.brat.core.interpolation.configdata.InterpolatorUtils.interpolateIfPresent;

/**
 * Interpolates every field of a {@link FlowControl}, including its repeat-until block.
 */
public final class FlowControlInterpolator implements ConfigDataInterpolator<FlowControl> {

    private final ConfigDataInterpolator<RepeatUntil> repeatUntilInterpolator;

    /**
     * Constructs an interpolator over the one it delegates the nested repeat-until block to.
     *
     * @param repeatUntilInterpolator the interpolator applied to the polling configuration
     */
    public FlowControlInterpolator(ConfigDataInterpolator<RepeatUntil> repeatUntilInterpolator) {
        this.repeatUntilInterpolator = repeatUntilInterpolator;
    }

    /**
     * Interpolates {@code waitAfter} and the nested repeat-until block.
     * <p>
     * The only outcome key this records is {@code waitAfter}, and only when it was declared. The
     * repeat-until block's outcomes stay on that block.
     *
     * @param target the flow-control block to interpolate
     * @param interpolation the interpolation implementation
     * @param runtimeData the runtime data
     * @return a new block with {@code waitAfter} interpolated and its repeat-until replaced by an
     *         interpolated copy. A {@code null} {@code waitAfter} yields no outcome and stays
     *         {@code null}; a {@code null} {@code repeatUntil} stays {@code null}
     * @throws dev.pbroman.brat.core.exception.BratException if {@code target} is already an
     *         interpolated copy, or if the repeat-until block fails to interpolate
     */
    @Override
    public FlowControl interpolated(FlowControl target, Interpolation interpolation, RuntimeData runtimeData) {
        checkNotInterpolated(target);

        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();

        var waitAfterOutcome =
                interpolateIfPresent(interpolation, runtimeData, outcomes, "waitAfter", target.getWaitAfter());
        var repeatUntilInterpolated = target.getRepeatUntil() == null
                ? null
                : repeatUntilInterpolator.interpolated(target.getRepeatUntil(), interpolation, runtimeData);

        return new FlowControl(asStringOrNull(waitAfterOutcome), repeatUntilInterpolated, outcomes);
    }
}
