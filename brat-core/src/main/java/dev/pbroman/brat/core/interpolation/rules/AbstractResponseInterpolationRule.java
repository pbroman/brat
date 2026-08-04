package dev.pbroman.brat.core.interpolation.rules;

import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.interpolation.InterpolationPatterns;
import org.apache.commons.lang3.StringUtils;

import static dev.pbroman.brat.core.interpolation.InterpolationChecks.requireNamespaces;
import static dev.pbroman.brat.core.util.Constants.RESPONSE_VARS;

/**
 * Base class for the interpolation rules resolving against the response of a previous request, which
 * share the need to check that the {@code responseVars} namespace holds the request being referenced
 * before trying to read anything out of it.
 */
public abstract class AbstractResponseInterpolationRule extends AbstractInterpolationRule {

    /**
     * Constructs a response interpolation rule.
     *
     * @param interpolationKey the namespace key this rule resolves, e.g. {@code responseVars}
     */
    protected AbstractResponseInterpolationRule(String interpolationKey) {
        super(interpolationKey);
    }

    /**
     * Whether this rule should decline {@code input}: blank, or not a token of this rule's
     * namespace.
     *
     * @param input the token to resolve
     * @param runtimeData the object containing values; must hold the {@code responseVars} namespace
     * @return whether the requirements for resolving are not met
     */
    protected boolean isRequirementsNotMet(String input, RuntimeData runtimeData) {
        requireNamespaces(runtimeData, RESPONSE_VARS);
        return StringUtils.isBlank(input) || !input.matches(InterpolationPatterns.regexForVariable(interpolationKey));
    }
}
