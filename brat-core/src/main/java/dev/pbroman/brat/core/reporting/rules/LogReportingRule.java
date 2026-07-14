package dev.pbroman.brat.core.reporting.rules;

import java.util.Map;
import java.util.stream.Collectors;

import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.api.reporting.ReportingRule;

/**
 * The core {@code "log"} {@link ReportingRule} — a single {@code logfmt}-style line
 * ({@code key="value"} pairs, space-separated, quote-escaped), meant for log aggregators
 * rather than a human reader.
 */
public final class LogReportingRule implements ReportingRule {

    private static final String KIND = "log";

    @Override
    public String report(String kind, Map<String, InterpolationOutcome> outcomes) {
        if (!KIND.equals(kind)) {
            return null;
        }
        return outcomes.entrySet().stream()
                .map(entry -> entry.getKey() + "=\"" + escape(entry.getValue().reportingString()) + "\"")
                .collect(Collectors.joining(" "));
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
