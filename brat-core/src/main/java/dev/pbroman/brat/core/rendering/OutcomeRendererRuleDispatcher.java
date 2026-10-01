package dev.pbroman.brat.core.rendering;

import java.util.Comparator;
import java.util.List;

import dev.pbroman.brat.core.api.rendering.OutcomeRenderer;
import dev.pbroman.brat.core.api.rendering.OutcomeRendererRule;
import dev.pbroman.brat.core.api.rendering.RenderTarget;
import dev.pbroman.brat.core.exception.BratException;

import static dev.pbroman.brat.core.util.Require.nonNull;

/**
 * Priority-ordered dispatcher over {@link OutcomeRendererRule}s: tries each rule in priority order
 * and returns the first rendering a rule does not decline.
 */
public class OutcomeRendererRuleDispatcher implements OutcomeRenderer {

    protected final List<OutcomeRendererRule> rules;

    /**
     * Constructs a dispatcher with a list of rules. Sorts the rules according to priority.
     *
     * @param rules the {@link OutcomeRendererRule}s
     */
    public OutcomeRendererRuleDispatcher(List<OutcomeRendererRule> rules) {
        this.rules = rules.stream()
                .sorted(Comparator.comparingInt(OutcomeRendererRule::priority).reversed())
                .toList();
    }

    @Override
    public String render(String kind, RenderTarget target) {
        nonNull(kind, "Cannot render for a null kind");
        nonNull(target, "Cannot render a null target");
        for (var rule : rules) {
            var result = rule.render(kind, target);
            if (result.isPresent()) {
                return result.get();
            }
        }
        throw new BratException(String.format("No OutcomeRendererRule recognizes render kind '%s'", kind));
    }
}
