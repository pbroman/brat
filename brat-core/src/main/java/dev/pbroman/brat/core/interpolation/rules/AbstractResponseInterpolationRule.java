package dev.pbroman.brat.core.interpolation.rules;

import static dev.pbroman.brat.core.interpolation.InterpolationChecks.requireNamespaces;
import static dev.pbroman.brat.core.util.Constants.RESPONSE_VARS;

import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.interpolation.InterpolationPatterns;
import org.apache.commons.lang3.StringUtils;

/**
 * Base class for the interpolation rules resolving against the response of a previous request, which
 * share the need to check that the {@code responseVars} namespace holds the request being referenced
 * before trying to read anything out of it.
 */
public abstract class AbstractResponseInterpolationRule extends AbstractInterpolationRule {

    /**
     * @param interpolationKey the namespace key this rule resolves, e.g. {@code responseVars}
     * @param patterns the patterns tokens are matched with
     */
    protected AbstractResponseInterpolationRule(String interpolationKey, InterpolationPatterns patterns) {
        super(interpolationKey, patterns);
    }

    protected boolean isRequirementsNotMet(String input, RuntimeData runtimeData) {
        requireNamespaces(runtimeData, RESPONSE_VARS);
        return StringUtils.isBlank(input) || !input.matches(patterns.getRegexForVariable(interpolationKey));
    }

}
