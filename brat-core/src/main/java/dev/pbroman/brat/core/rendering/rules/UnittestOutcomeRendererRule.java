package dev.pbroman.brat.core.rendering.rules;

import java.util.stream.Collectors;

import dev.pbroman.brat.core.api.rendering.OutcomeRendererRule;
import dev.pbroman.brat.core.api.rendering.RenderTarget;

/**
 * The core {@code "unittest"} {@link OutcomeRendererRule} — a {@code toString()}/record-style
 * {@code Label[key=value, ...]} dump, scoped to interpolation outcomes only, with the label
 * omitted when the target has none.
 * <p>
 * A target with no outcomes renders as empty brackets, keeping the shape readable at a glance.
 */
public final class UnittestOutcomeRendererRule implements OutcomeRendererRule {

    private static final String KIND = "unittest";

    @Override
    public String render(String kind, RenderTarget target) {
        if (!KIND.equals(kind)) {
            return null;
        }
        var fields = target.outcomes().entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue().reportingString())
                .collect(Collectors.joining(", ", "[", "]"));
        return target.label() == null ? fields : target.label() + fields;
    }
}
