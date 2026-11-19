package dev.pbroman.brat.core.rendering.rules;

import java.util.Optional;
import java.util.stream.Collectors;

import dev.pbroman.brat.core.api.rendering.OutcomeRendererRule;
import dev.pbroman.brat.core.api.rendering.RenderKinds;
import dev.pbroman.brat.core.api.rendering.RenderTarget;

/**
 * The core {@code "unittest"} ({@link RenderKinds#UNITTEST}) {@link OutcomeRendererRule} — a {@code
 * toString()}/record-style {@code Label[key=value, ...]} dump, scoped to interpolation outcomes
 * only, with the label omitted when the target has none.
 * <p>
 * A target with no outcomes renders as empty brackets, keeping the shape readable at a glance.
 */
public final class UnittestOutcomeRendererRule implements OutcomeRendererRule {

    @Override
    public Optional<String> render(String kind, RenderTarget target) {
        if (!RenderKinds.UNITTEST.equals(kind)) {
            return Optional.empty();
        }
        var fields = target.outcomes().entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue().reportingString())
                .collect(Collectors.joining(", ", "[", "]"));
        return Optional.of(target.label() == null ? fields : target.label() + fields);
    }
}
