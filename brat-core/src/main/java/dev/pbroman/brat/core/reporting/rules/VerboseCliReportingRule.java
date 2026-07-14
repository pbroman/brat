package dev.pbroman.brat.core.reporting.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.api.reporting.ReportingRule;

/**
 * The core {@code "verbose-cli"} {@link ReportingRule} — outcomes grouped into sections by the
 * prefix before the first {@code "."} in each key (e.g. {@code "header."}, {@code "auth."}),
 * ungrouped fields printed first.
 */
public final class VerboseCliReportingRule implements ReportingRule {

    private static final String KIND = "verbose-cli";

    @Override
    public String report(String kind, Map<String, InterpolationOutcome> outcomes) {
        if (!KIND.equals(kind)) {
            return null;
        }

        var grouped = new LinkedHashMap<String, List<Map.Entry<String, InterpolationOutcome>>>();
        for (var entry : outcomes.entrySet()) {
            var dot = entry.getKey().indexOf('.');
            var group = dot < 0 ? "" : entry.getKey().substring(0, dot);
            grouped.computeIfAbsent(group, g -> new ArrayList<>()).add(entry);
        }

        var sections = new ArrayList<String>();
        grouped.forEach((group, entries) -> {
            var lines = entries.stream()
                    .map(entry -> "  " + fieldName(group, entry.getKey()) + ": " + entry.getValue().reportingString())
                    .collect(Collectors.joining(System.lineSeparator()));
            sections.add(group.isEmpty() ? lines : capitalize(group) + ":" + System.lineSeparator() + lines);
        });
        return String.join(System.lineSeparator(), sections);
    }

    private String fieldName(String group, String key) {
        return group.isEmpty() ? key : key.substring(group.length() + 1);
    }

    private String capitalize(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
