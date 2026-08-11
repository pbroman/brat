package dev.pbroman.brat.core.interpolation.configdata;

import java.util.ArrayList;
import java.util.LinkedHashMap;

import dev.pbroman.brat.core.api.interpolation.ConfigDataInterpolator;
import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.data.Assertion;
import dev.pbroman.brat.core.data.ChainedCondition;
import dev.pbroman.brat.core.data.runtime.RuntimeData;

import static dev.pbroman.brat.core.interpolation.configdata.InterpolatorUtils.asStringOrNull;
import static dev.pbroman.brat.core.interpolation.configdata.InterpolatorUtils.checkNotInterpolated;
import static dev.pbroman.brat.core.interpolation.configdata.InterpolatorUtils.interpolateArgs;
import static dev.pbroman.brat.core.interpolation.configdata.InterpolatorUtils.interpolateIfPresent;
import static dev.pbroman.brat.core.interpolation.configdata.InterpolatorUtils.interpolateStructure;

/**
 * Interpolates every field of an {@link Assertion}, including its chain.
 * <p>
 * An assertion is a {@link dev.pbroman.brat.core.data.Condition} with a {@code message}, a
 * {@code severity} and a chain of further conditions over the same {@code a}. It needs its own
 * interpolator rather than borrowing {@code ConditionInterpolator} for two reasons: interpolating it
 * as a condition would return a plain condition, losing the three fields it adds; and its {@code a}
 * must be interpolated <em>once</em> and shared with every link, since a chain exists to test one
 * value several ways.
 */
public final class AssertionInterpolator implements ConfigDataInterpolator<Assertion> {

    private final ConfigDataInterpolator<ChainedCondition> chainedConditionInterpolator;

    /**
     * Constructs an assertion interpolator over the interpolator it delegates each chain link to.
     *
     * @param chainedConditionInterpolator the interpolator applied to every entry of the chain
     */
    public AssertionInterpolator(ConfigDataInterpolator<ChainedCondition> chainedConditionInterpolator) {
        this.chainedConditionInterpolator = chainedConditionInterpolator;
    }

    /**
     * Interpolates the assertion's {@code a}, {@code b}, {@code args} and {@code message}, and every
     * link of its chain.
     * <p>
     * {@code func} is a name, not a value, and is copied through uninterpolated. {@code severity} is
     * an enum and is copied through as-is. Each chain link is interpolated by the injected
     * {@code ChainedCondition} interpolator and appears on the copy in declaration order.
     * <p>
     * Outcome keys are {@code a}, {@code b}, {@code args.<name>} and {@code message}, in that order —
     * the same keys {@code ConditionInterpolator} produces plus {@code message}. A link's own
     * outcomes stay on that link and are <em>not</em> merged into this map, so an assertion's outcome
     * keys do not depend on how long its chain is.
     *
     * @param target the assertion to interpolate
     * @param interpolation the interpolation implementation
     * @param runtimeData the runtime data
     * @return a new assertion with every interpolatable field and every chain link interpolated. A
     *         {@code null} {@code b} or {@code message} yields no outcome for that field and stays
     *         {@code null} on the copy; an empty chain yields an empty chain
     * @throws dev.pbroman.brat.core.exception.BratException if {@code target} is already an
     *         interpolated copy, or if an {@code args} entry has a {@code null} value. A link that
     *         fails to interpolate propagates, so a chain is interpolated wholly or not at all
     */
    @Override
    public Assertion interpolated(Assertion target, Interpolation interpolation, RuntimeData runtimeData) {
        checkNotInterpolated(target);

        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();
        var aValue = interpolateStructure(interpolation, runtimeData, outcomes, "a", target.getA());
        var bValue = interpolateStructure(interpolation, runtimeData, outcomes, "b", target.getB());
        var argsValues = interpolateArgs(target.getArgs(), interpolation, runtimeData, outcomes);
        var messageValue = asStringOrNull(
                interpolateIfPresent(interpolation, runtimeData, outcomes, "message", target.getMessage()));

        var chainValues = new ArrayList<ChainedCondition>();
        for (ChainedCondition chainedCondition : target.getChain()) {
            chainValues.add(chainedConditionInterpolator.interpolated(chainedCondition, interpolation, runtimeData));
        }

        var interpolated = new Assertion(target.getFunc(), aValue, bValue, chainValues, messageValue, outcomes);
        interpolated.setArgs(argsValues);
        interpolated.setSeverity(target.getSeverity());
        return interpolated;
    }
}
