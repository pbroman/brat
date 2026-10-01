package dev.pbroman.brat.core.rendering.rules;

import java.util.Optional;
import java.util.stream.Collectors;

import dev.pbroman.brat.core.api.rendering.OutcomeRendererRule;
import dev.pbroman.brat.core.api.rendering.RenderTarget;

/**
 * The core {@code "console"} {@link OutcomeRendererRule} — one line per outcome, in field order,
 * under the target's label where it has one.
 * <p>
 * A target with no outcomes renders as its label alone, or as the empty string when it has no label
 * either — never as a header followed by nothing.
 */
public final class ConsoleOutcomeRendererRule implements OutcomeRendererRule {

    private static final String KIND = "console";

    @Override
    public Optional<String> render(String kind, RenderTarget target) {
        if (!KIND.equals(kind)) {
            return Optional.empty();
        }
        var lines = target.outcomes().entrySet().stream()
                .map(entry -> entry.getKey() + ": " + entry.getValue().reportingString())
                .collect(Collectors.joining(System.lineSeparator()));
        if (target.label() == null) {
            return Optional.of(lines);
        }
        var header = target.label() + ":";
        return Optional.of(lines.isEmpty() ? header : header + System.lineSeparator() + lines);
    }
}
