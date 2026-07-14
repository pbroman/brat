package dev.pbroman.brat.core.reporting;

import static dev.pbroman.brat.core.util.ExceptionUtil.bratExceptionOnNull;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.api.reporting.ReportingEngine;
import dev.pbroman.brat.core.api.reporting.ReportingRule;
import dev.pbroman.brat.core.exception.BratException;

/**
 * Priority-ordered dispatcher over {@link ReportingRule}s: tries each rule in priority order
 * and returns the first non-null report.
 */
public class ReportingEngineDispatcher implements ReportingEngine {

    protected final List<ReportingRule> rules;

    /**
     * Constructs a dispatcher with a list of rules. Sorts the rules according to priority.
     *
     * @param rules the {@link ReportingRule}s
     */
    public ReportingEngineDispatcher(List<ReportingRule> rules) {
        this.rules = rules.stream().sorted(Comparator.comparingInt(ReportingRule::priority).reversed()).toList();
    }

    @Override
    public String report(String kind, Map<String, InterpolationOutcome> outcomes) {
        bratExceptionOnNull(kind, "Cannot produce a report for a null kind");
        for (var rule : rules) {
            var result = rule.report(kind, outcomes);
            if (result != null) {
                return result;
            }
        }
        throw new BratException(String.format("No ReportingRule recognizes report kind '%s'", kind));
    }
}
