package dev.pbroman.brat.core.interpolation;

import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;

import static dev.pbroman.brat.core.interpolation.InterpolationChecks.requireNamespaces;
import static dev.pbroman.brat.core.interpolation.InterpolationPatterns.FUNCTION_CALL_PREFIX;
import static dev.pbroman.brat.core.util.Require.nonNull;

/**
 * Top-level {@link Interpolation} entry point: finds every {@code ${...}} token in a whole
 * field string, resolves each in isolation, and splices the results back into the original string.
 * <p>
 * Resolving one token means choosing between two resolvers, and that choice is this class's alone:
 * a token beginning {@code ${__} } is a function call and goes to the {@link FunctionEvaluator};
 * anything else is a namespace lookup and goes to the {@link InterpolationRuleDispatcher}. The
 * evaluator is handed this scanner so that a call's arguments resolve as fields do — which is what
 * lets an argument hold a nested token or another call.
 */
public class InterpolationScanner implements Interpolation {

    private final Interpolation dispatcher;

    private final FunctionEvaluator functionEvaluator;

    /**
     * Constructs a scanner over the two resolvers a token can be routed to.
     *
     * @param dispatcher resolves a namespace lookup, e.g. {@code ${vars.name}}
     * @param functionEvaluator resolves a function call, e.g. {@code ${__uuid}}
     */
    public InterpolationScanner(Interpolation dispatcher, FunctionEvaluator functionEvaluator) {
        this.dispatcher = dispatcher;
        this.functionEvaluator = functionEvaluator;
    }

    /**
     * Resolves every {@code ${...}} token in {@code input}, splicing each one's resolved value
     * back into the original string.
     * <p>
     * Tokens are found by {@link TokenScanner}, which counts braces — so a token holding another token
     * ({@code ${__upper(${vars.name})}}) is passed on whole rather than truncated at the inner brace,
     * and resolving the nested part is the resolving collaborator's business.
     * <p>
     * <strong>Each token is routed by its prefix.</strong> One beginning {@code ${__} } goes to the
     * {@link FunctionEvaluator}, everything else to the dispatcher. A call therefore never reaches a
     * rule, whatever that rule's priority, and an unknown function name fails rather than passing
     * through — unlike an unrecognised namespace, which passes through so that a field holding
     * {@code ${HOME}} survives.
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
     * @throws BratException if {@code input} or {@code runtimeData} is {@code null}, or any
     *         token's resolution throws
     */
    @Override
    public InterpolationOutcome outcome(String input, RuntimeData runtimeData) {
        nonNull(input, "Cannot interpolate a null input");
        requireNamespaces(runtimeData);
        var tokens = TokenScanner.tokensIn(input);
        if (tokens.size() == 1
                && tokens.getFirst().start() == 0
                && tokens.getFirst().end() == input.length()) {
            return resolveToken(input, runtimeData);
        }
        // Built front to back, resolving each token as its position is reached: the literal text
        // before the token, then that token's own resolved value. Resolving per occurrence is what
        // matters — replacing by text would substitute every copy of a repeated token from a single
        // resolution, so `${__uuid} / ${__uuid}` would yield one UUID twice and never call the
        // second token.
        var value = new StringBuilder(input.length());
        var maskedFinal = new StringBuilder(input.length());
        var containsSecret = false;
        var cursor = 0;
        for (var token : tokens) {
            value.append(input, cursor, token.start());
            maskedFinal.append(input, cursor, token.start());
            var tokenOutcome = resolveToken(token.text(), runtimeData);
            containsSecret |= tokenOutcome.containsSecret();
            value.append(tokenOutcome.asString());
            maskedFinal.append(tokenOutcome.containsSecret() ? "***" : tokenOutcome.asString());
            cursor = token.end();
        }
        value.append(input, cursor, input.length());
        maskedFinal.append(input, cursor, input.length());
        var resolved = value.toString();
        var masked = maskedFinal.toString();
        if (masked.equals(input)) {
            return new InterpolationOutcome(resolved, resolved, containsSecret);
        }
        return new InterpolationOutcome(resolved, input + " → " + masked, containsSecret);
    }

    /**
     * Routes one token to the resolver its prefix names: the function evaluator for a call, the
     * dispatcher for anything else.
     */
    private InterpolationOutcome resolveToken(String token, RuntimeData runtimeData) {
        return token.startsWith(FUNCTION_CALL_PREFIX)
                ? functionEvaluator.evaluate(token, this, runtimeData)
                : dispatcher.outcome(token, runtimeData);
    }
}
