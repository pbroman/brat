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
 * and returns the first non-null rendering.
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

    /**
     * Unlike a single {@link OutcomeRendererRule}, the dispatcher never returns {@code null} —
     * strengthened from {@link OutcomeRenderer#render}'s general contract to guarantee a rendering
     * as long as some rule recognizes {@code kind}.
     *
     * @return the rendered text; never {@code null}
     * @throws BratException if {@code kind} or {@code target} is {@code null}, or no rule
     *         recognizes {@code kind}
     */
    @Override
    public String render(String kind, RenderTarget target) {
        nonNull(kind, "Cannot render for a null kind");
        nonNull(target, "Cannot render a null target");
        for (var rule : rules) {
            var result = rule.render(kind, target);
            if (result != null) {
                return result;
            }
        }
        throw new BratException(String.format("No OutcomeRendererRule recognizes render kind '%s'", kind));
    }
}
