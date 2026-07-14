package dev.pbroman.brat.core.api.reporting;

import java.util.Map;

import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;

/**
 * A single, priority-dispatched {@link ReportingEngine} rule.
 */
public interface ReportingRule extends ReportingEngine {

    /**
     * Returns the priority of the rule. Rules with higher priority are executed before ones with
     * lower.
     * <p>
     * Priorities 0-100 are reserved for the core code.
     *
     * @return the priority
     */
    default int priority() {
        return 0;
    }

    /**
     * @return the produced report if this rule recognizes {@code kind}, or {@code null} to let
     *         the dispatcher try the next rule
     */
    @Override
    String report(String kind, Map<String, InterpolationOutcome> outcomes);
}
