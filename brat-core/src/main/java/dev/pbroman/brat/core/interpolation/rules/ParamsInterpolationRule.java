package dev.pbroman.brat.core.interpolation.rules;

import static dev.pbroman.brat.core.util.CheckUtils.checkInterpolationArgs;
import static dev.pbroman.brat.core.util.Constants.PARAMS;

import dev.pbroman.brat.core.api.interpolation.InterpolationRule;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.tools.InterpolationTools;

/**
 * An {@link InterpolationRule} for execution-time parameters — values injected at suite launch
 * (e.g. via a CLI flag) rather than defined in the test suite itself, for flow-control and
 * performance concerns (thread counts, load profiles, skipping assertions) that shouldn't live
 * in {@code env} or {@code constants}.
 */
public final class ParamsInterpolationRule extends AbstractInterpolationRule {

    /**
     * Constructs an {@link InterpolationRule} for execution-time parameters.
     *
     * @param tools the {@link InterpolationTools}
     */
    public ParamsInterpolationRule(InterpolationTools tools) {
        super(PARAMS, tools);
    }

    @Override
    public String resolve(String input, RuntimeData runtimeData) {
        checkInterpolationArgs(input, runtimeData, PARAMS);
        return simpleInterpolation(input, runtimeData, runtimeData.getParams());
    }

    /**
     * {@inheritDoc}
     *
     * @throws BratException on missing replacement for a parameter with no fallback declared.
     */
    @Override
    protected String onMissingReplacement(String placeholder, String input) {
        throw new BratException("The parameter '" + placeholder + "' is not set.");
    }

}
