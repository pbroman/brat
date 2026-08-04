package dev.pbroman.brat.core.interpolation.rules;

import dev.pbroman.brat.core.api.interpolation.InterpolationRule;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import lombok.extern.slf4j.Slf4j;

import static dev.pbroman.brat.core.interpolation.InterpolationChecks.requireNamespaces;
import static dev.pbroman.brat.core.util.Constants.CONSTANTS;

/**
 * An {@link InterpolationRule} for constants.
 */
@Slf4j
public final class ConstantsInterpolationRule extends AbstractInterpolationRule {

    /**
     * Constructs an {@link InterpolationRule} for constants.
     *
     */
    public ConstantsInterpolationRule() {
        super(CONSTANTS);
    }

    @Override
    public String resolve(String input, RuntimeData runtimeData) {
        requireNamespaces(runtimeData, CONSTANTS);
        return simpleInterpolation(input, runtimeData, runtimeData.getConstants());
    }

    /**
     * {@inheritDoc}
     *
     * @throws BratException on missing replacement for a constant.
     */
    @Override
    protected String onMissingReplacement(String placeholder, String input) {
        throw new BratException("The constant '" + placeholder + "' is not set.");
    }
}
