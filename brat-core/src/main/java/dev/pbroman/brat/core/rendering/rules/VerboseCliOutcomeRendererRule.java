package dev.pbroman.brat.core.rendering.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.api.rendering.OutcomeRendererRule;
import dev.pbroman.brat.core.api.rendering.RenderTarget;

/**
 * The core {@code "verbose-cli"} {@link OutcomeRendererRule} — outcomes grouped into sections by the
 * prefix before the first {@code "."} in each key (e.g. {@code "header."}, {@code "auth."}),
 * ungrouped fields printed first, under the target's label where it has one.
 * <p>
 * A target with no outcomes renders as its label alone, or as the empty string when it has no label
 * either — never as a header followed by nothing.
 */
public final class VerboseCliOutcomeRendererRule implements OutcomeRendererRule {

    private static final String KIND = "verbose-cli";

    @Override
    public String render(String kind, RenderTarget target) {
        if (!KIND.equals(kind)) {
            return null;
        }

        var grouped = new LinkedHashMap<String, List<Map.Entry<String, InterpolationOutcome>>>();
        for (var entry : target.outcomes().entrySet()) {
            var dot = entry.getKey().indexOf('.');
            var group = dot < 0 ? "" : entry.getKey().substring(0, dot);
            grouped.computeIfAbsent(group, g -> new ArrayList<>()).add(entry);
        }

        var sections = new ArrayList<String>();
        if (target.label() != null) {
            sections.add(target.label() + ":");
        }
        grouped.forEach((group, entries) -> {
            var lines = entries.stream()
                    .map(entry -> "  " + fieldName(group, entry.getKey()) + ": "
                            + entry.getValue().reportingString())
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
