package dev.pbroman.brat.core.interpolation;

import java.util.Comparator;
import java.util.List;

import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.api.interpolation.InterpolationRule;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;

import static dev.pbroman.brat.core.util.ExceptionUtil.bratExceptionOnNull;

/**
 * Priority-ordered, chaining dispatcher over {@link InterpolationRule}s: each rule's outcome
 * becomes the next rule's input, so a rule that only preprocesses a token (e.g. expanding a
 * shorthand form into another token) and a rule that actually resolves a value can be composed
 * in one pass.
 */
public class InterpolationRuleDispatcher implements Interpolation {

    protected final List<InterpolationRule> rules;

    /**
     * Constructs a dispatcher with a list of rules. Sorts the rules according to priority.
     *
     * @param rules the {@link InterpolationRule}s
     */
    public InterpolationRuleDispatcher(List<InterpolationRule> rules) {
        this.rules = rules.stream().sorted(Comparator.comparingInt(InterpolationRule::priority).reversed()).toList();
    }

    /**
     * Resolves {@code input} by feeding it through every rule in priority order, each rule's
     * resolved value becoming the next rule's input. The returned outcome's
     * {@code reportingString} always compares the original {@code input} directly to the final
     * resolved value — never an intermediate hop — masked with {@code ***} if any rule in the
     * chain tagged its result as a secret.
     * <p>
     * Substitution is detected by comparing the final resolved value to the original — if it
     * happens to equal the original, this reports it as unchanged even if a rule in the chain
     * actually substituted a value. The returned {@code value} and secret-tagging are unaffected
     * and remain correct regardless.
     *
     * @param input the variable to be interpolated
     * @param runtimeData the object containing values
     * @return the outcome of feeding {@code input} through every rule in priority order; equal
     *         to {@code input} itself (value and reporting string) if no rule changed it
     * @throws BratException if {@code input} is {@code null}, or if any rule in the chain throws it
     */
    @Override
    public InterpolationOutcome outcome(String input, RuntimeData runtimeData) {
        bratExceptionOnNull(input, "Cannot interpolate a null input");
        var current = input;
        var containsSecret = false;
        for (var rule : rules) {
            var next = rule.outcome(current, runtimeData);
            containsSecret |= next.containsSecret();
            current = next.asString();
        }
        if (current.equals(input)) {
            return new InterpolationOutcome(current, current, containsSecret);
        }
        var displayValue = containsSecret ? "***" : current;
        return new InterpolationOutcome(current, input + " → " + displayValue, containsSecret);
    }
}