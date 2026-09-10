package dev.pbroman.brat.core.interpolation;

import java.util.Comparator;
import java.util.List;

import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.api.interpolation.InterpolationRule;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;

import static dev.pbroman.brat.core.util.Require.nonNull;

/**
 * Priority-ordered, first-match dispatcher over {@link InterpolationRule}s: the first rule that
 * claims a token resolves it, and no rule below it is consulted.
 * <p>
 * <strong>First-match, not a chain.</strong> A rule's output is never fed to another rule, so a
 * value produced from data is never re-examined as though it were authored text — which is what
 * keeps a captured response field holding the text {@code ${secrets.apiKey}} from resolving to a
 * secret.
 */
public class InterpolationRuleDispatcher implements Interpolation {

    protected final List<InterpolationRule> rules;

    /**
     * Constructs a dispatcher with a list of rules. Sorts the rules according to priority.
     *
     * @param rules the {@link InterpolationRule}s
     */
    public InterpolationRuleDispatcher(List<InterpolationRule> rules) {
        this.rules = rules.stream()
                .sorted(Comparator.comparingInt(InterpolationRule::priority).reversed())
                .toList();
    }

    /**
     * Resolves {@code input} with the first rule that claims it.
     * <p>
     * Rules are consulted in priority order and the first present outcome is returned as it came —
     * value, type and secret tag all the rule's. No further rule runs, and <strong>the returned
     * value is never itself interpolated</strong>.
     * <p>
     * Where <em>no</em> rule claims the token, it is returned unchanged rather than reported as an
     * error, so that a field holding {@code ${HOME}} survives a run instead of failing it. An
     * unrecognized token is therefore indistinguishable in the result from one a rule resolved to its
     * own text.
     * <p>
     * The {@code reportingString} compares {@code input} to the resolved value, masked as
     * {@code ***} where the resolving rule tagged its outcome as secret; the returned {@code value}
     * is always the real, unmasked one. A resolved value that happens to equal {@code input} is
     * reported as unchanged.
     *
     * @param input the token to be interpolated
     * @param runtimeData the object containing values
     * @return the first claiming rule's outcome, or an outcome equal to {@code input} in both value
     *         and reporting string if no rule claimed it
     * @throws BratException if {@code input} is {@code null}, or if the claiming rule throws it.
     *         A rule that throws ends the dispatch: the failure is not swallowed and the remaining
     *         rules are not tried, since a rule throwing has claimed the token and failed on it
     */
    @Override
    public InterpolationOutcome outcome(String input, RuntimeData runtimeData) {
        nonNull(input, "Cannot interpolate a null input");
        for (var rule : rules) {
            var claimed = rule.outcome(input, runtimeData);
            if (claimed.isPresent()) {
                return reported(input, claimed.get());
            }
        }
        return new InterpolationOutcome(input, input);
    }

    /**
     * Re-reports a claiming rule's outcome against the original input, keeping its value and secret
     * tag. The value is carried through untouched rather than stringified, so a rule that resolved to
     * a {@code List} or an {@code Integer} still returns one.
     */
    private static InterpolationOutcome reported(String input, InterpolationOutcome outcome) {
        var resolved = outcome.asString();
        if (resolved.equals(input)) {
            return new InterpolationOutcome(outcome.value(), resolved, outcome.containsSecret());
        }
        var displayValue = outcome.containsSecret() ? "***" : resolved;
        return new InterpolationOutcome(outcome.value(), input + " → " + displayValue, outcome.containsSecret());
    }
}
