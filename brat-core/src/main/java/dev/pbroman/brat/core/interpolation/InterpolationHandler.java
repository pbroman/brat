package dev.pbroman.brat.core.interpolation;

import static dev.pbroman.brat.core.util.CheckUtils.checkInterpolationArgs;
import static dev.pbroman.brat.core.util.ExceptionUtil.bratExceptionOnNull;

import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.tools.InterpolationTools;

/**
 * Top-level {@link Interpolation} entry point: finds every {@code ${...}} token in a whole
 * field string, resolves each in isolation via the {@link InterpolationRuleDispatcher}, and
 * splices the results back into the original string.
 */
public class InterpolationHandler implements Interpolation {

    private final Interpolation dispatcher;

    private final InterpolationTools tools;

    /**
     * Constructs an interploation handler with a dispatcher and {@link InterpolationTools}.
     *
     * @param dispatcher the interpolation dispatcher
     * @param tools the {@link InterpolationTools}
     */
    public InterpolationHandler(Interpolation dispatcher, InterpolationTools tools) {
        this.dispatcher = dispatcher;
        this.tools = tools;
    }

    /**
     * Resolves every {@code ${...}} token in {@code input}, splicing each one's resolved value
     * back into the original string. The returned outcome's {@code reportingString} compares the
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
        bratExceptionOnNull(input, "Cannot interpolate a null input");
        checkInterpolationArgs(input, runtimeData);
        var matcher = tools.getVariablePattern().matcher(input);
        var value = input;
        var maskedFinal = input;
        var containsSecret = false;
        while (matcher.find()) {
            var token = matcher.group(0);
            var tokenOutcome = dispatcher.outcome(token, runtimeData);
            value = value.replace(token, tokenOutcome.asString());
            var displayValue = tokenOutcome.containsSecret() ? "***" : tokenOutcome.asString();
            maskedFinal = maskedFinal.replace(token, displayValue);
            containsSecret |= tokenOutcome.containsSecret();
        }
        if (maskedFinal.equals(input)) {
            return new InterpolationOutcome(value, value, containsSecret);
        }
        return new InterpolationOutcome(value, input + " → " + maskedFinal, containsSecret);
    }
}
