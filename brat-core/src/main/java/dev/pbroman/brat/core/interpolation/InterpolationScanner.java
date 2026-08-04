package dev.pbroman.brat.core.interpolation;

import java.util.ArrayList;

import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;

import static dev.pbroman.brat.core.interpolation.InterpolationChecks.requireNamespaces;
import static dev.pbroman.brat.core.util.Require.nonNull;

/**
 * Top-level {@link Interpolation} entry point: finds every {@code ${...}} token in a whole
 * field string, resolves each in isolation via the {@link InterpolationRuleDispatcher}, and
 * splices the results back into the original string.
 */
public class InterpolationScanner implements Interpolation {

    private final Interpolation dispatcher;

    /**
     * Constructs an interpolation handler with a dispatcher and {@link InterpolationPatterns}.
     *
     * @param dispatcher the interpolation dispatcher
     */
    public InterpolationScanner(Interpolation dispatcher) {
        this.dispatcher = dispatcher;
    }

    /**
     * Resolves every {@code ${...}} token in {@code input}, splicing each one's resolved value
     * back into the original string.
     * <p>
     * Tokens are found by {@link TokenScanner}, which counts braces — so a token holding another token
     * ({@code ${__upper(${vars.name})}}) is passed to the dispatcher whole rather than truncated at the
     * inner brace, and resolving the nested part is the resolving rule's business.
     * <p>
     * <strong>A field that is nothing but a single token keeps its resolved value's type</strong> —
     * the outcome is returned as the resolving rule produced it, so {@code ${response.json.$.items}}
     * yields a {@code List} rather than its text form. Only a token embedded in surrounding text is
     * stringified, because there is no other way to splice it back in. That distinction is the one a
     * rule cannot make for itself: it answers about one token and knows nothing of the surrounding field.
     * The returned outcome's {@code reportingString} compares the
     * original {@code input} directly to the final resolved value, with any token tagged as a
     * secret by the dispatcher masked as {@code ***} in that display value only — the returned
     * {@code value} always holds the real, unmasked result.
     * <p>
     * Substitution is detected by comparing each token's resolved value to its own token text —
     * if a resolved value happens to equal that text, this reports it as an unchanged passthrough
     * even though a real substitution occurred. The returned {@code value} and secret-tagging are
     * unaffected and remain correct regardless.
     *
     * @param input the field string to resolve, may contain any number of tokens
     * @param runtimeData the object containing values
     * @return the outcome of resolving every token in {@code input}; equal to {@code input}
     *         itself (value and reporting string) if it contained no tokens or none resolved to
     *         a different value
     * @throws BratException if {@code input} is {@code null}, or any token's resolution throws
     * @throws IllegalArgumentException if {@code runtimeData} is {@code null}
     */
    @Override
    public InterpolationOutcome outcome(String input, RuntimeData runtimeData) {
        nonNull(input, "Cannot interpolate a null input");
        requireNamespaces(runtimeData);
        var tokens = TokenScanner.tokensIn(input);
        if (tokens.size() == 1
                && tokens.getFirst().start() == 0
                && tokens.getFirst().end() == input.length()) {
            return dispatcher.outcome(input, runtimeData);
        }
        // Resolved in reading order, then spliced back to front. Both halves matter: replacing by
        // text would substitute every copy of a repeated token from a single resolution, so
        // `${__uuid} / ${__uuid}` would yield one UUID twice and never call the second token;
        // splicing backwards keeps the earlier tokens' indices valid as later ones are replaced.
        var outcomes = new ArrayList<InterpolationOutcome>(tokens.size());
        var containsSecret = false;
        for (var token : tokens) {
            var tokenOutcome = dispatcher.outcome(token.text(), runtimeData);
            outcomes.add(tokenOutcome);
            containsSecret |= tokenOutcome.containsSecret();
        }
        var value = new StringBuilder(input);
        var maskedFinal = new StringBuilder(input);
        for (var index = tokens.size() - 1; index >= 0; index--) {
            var token = tokens.get(index);
            var tokenOutcome = outcomes.get(index);
            value.replace(token.start(), token.end(), tokenOutcome.asString());
            maskedFinal.replace(
                    token.start(), token.end(), tokenOutcome.containsSecret() ? "***" : tokenOutcome.asString());
        }
        var resolved = value.toString();
        var masked = maskedFinal.toString();
        if (masked.equals(input)) {
            return new InterpolationOutcome(resolved, resolved, containsSecret);
        }
        return new InterpolationOutcome(resolved, input + " → " + masked, containsSecret);
    }
}
