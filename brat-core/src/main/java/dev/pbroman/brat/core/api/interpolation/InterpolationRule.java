package dev.pbroman.brat.core.api.interpolation;

import dev.pbroman.brat.core.interpolation.rules.ResponseShorthandInterpolationRule;

public interface InterpolationRule extends Interpolation {

    /**
     * Returns the priority of the rule. Rules with higher priority are executed before ones with lower.
     * <p>
     * Giving rules priority makes it possible to override core rules, since a rule is only executed if the input
     * matches the variable regex. You may thus define a new rule for a pattern, implement it to return a string not
     * matching the variable pattern, and give it a higher priority than the original rule.
     * </p>
     * Using the same pattern, you may also define rules preprocessing variables. The
     * {@link ResponseShorthandInterpolationRule} is an example of such a rule.
     * <p>
     * Priorities 0-100 are reserved for the core code.
     * </p>
     *
     * @return the priority
     */
    default int priority() {
        return 0;
    }
}
