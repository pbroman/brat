package dev.pbroman.brat.core.reporting.rules;

import java.util.Map;
import java.util.stream.Collectors;

import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.api.reporting.ReportingRule;

/**
 * The core {@code "unittest"} {@link ReportingRule} — a {@code toString()}/record-style
 * {@code [key=value, ...]} dump, scoped to interpolation outcomes only.
 */
public final class UnittestReportingRule implements ReportingRule {

    private static final String KIND = "unittest";

    @Override
    public String report(String kind, Map<String, InterpolationOutcome> outcomes) {
        if (!KIND.equals(kind)) {
            return null;
        }
        return outcomes.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue().reportingString())
                .collect(Collectors.joining(", ", "[", "]"));
    }
}
