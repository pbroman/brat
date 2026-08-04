package dev.pbroman.brat.core.interpolation.rules;

import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.api.interpolation.InterpolationRule;
import dev.pbroman.brat.core.api.secrets.SecretsProvider;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.interpolation.InterpolationPatterns;

import static dev.pbroman.brat.core.interpolation.InterpolationChecks.requireNamespaces;
import static dev.pbroman.brat.core.util.Constants.SECRETS;
import static dev.pbroman.brat.core.util.Constants.VARIABLE_GROUP_NAME;
import static dev.pbroman.brat.core.util.Require.nonNull;

/**
 * An {@link InterpolationRule} resolving {@code ${secrets.key}} tokens against a
 * {@link SecretsProvider}.
 * <p>
 * Unlike the namespace rules it sits alongside, this rule implements {@link InterpolationRule}
 * directly rather than extending {@link AbstractInterpolationRule}: it resolves against a provider
 * chain rather than a {@link RuntimeData} map, it supports no {@code :-} fallback, and it must tag
 * its own outcome as secret-bearing, which the shared base implementation cannot do.
 */
public final class SecretsInterpolationRule implements InterpolationRule {

    private final SecretsProvider provider;

    /**
     * Constructs a rule resolving against a provider.
     *
     * @param provider the provider to resolve secrets against, typically a
     *        {@link dev.pbroman.brat.core.secrets.CompositeSecretsProvider}
     * @throws BratException if {@code provider} is {@code null}
     */
    public SecretsInterpolationRule(SecretsProvider provider) {
        nonNull(provider, "The secrets provider must be set");
        this.provider = provider;
    }

    /**
     * Resolves a {@code ${secrets.key}} token to its secret value.
     * <p>
     * The whole text captured after {@code secrets.} is the key: {@code :-} carries no special
     * meaning here, so {@code ${secrets.apiKey:-env.fallback}} looks up a secret literally named
     * {@code apiKey:-env.fallback} and fails if none exists. Secrets deliberately have no fallback
     * — falling back across sources is what the provider chain is for.
     * <p>
     * A token with no key at all ({@code ${secrets.}}) is a malformed reference and fails; no
     * lookup is attempted, so a {@link SecretsProvider} is never asked for a {@code null} key.
     * <p>
     * {@code runtimeData} holds no secrets and is not consulted; it is still rejected when
     * {@code null}, so that a null-argument failure does not depend on which rule the dispatcher
     * happens to reach first.
     *
     * @param input the token to resolve, e.g. {@code "${secrets.apiKey}"}
     * @param runtimeData the object containing values; unused beyond the null check
     * @return an outcome holding the resolved secret, with {@code containsSecret} set and a
     *         {@code reportingString} of {@code input + " → ***"} that never contains the value
     *         itself; or {@code input} unchanged with {@code containsSecret} unset if {@code input}
     *         is not a {@code ${secrets.…}} token, leaving it for another rule to process
     * @throws BratException if {@code input} is {@code null}, if {@code input} is a
     *         {@code ${secrets.…}} token with no key, if it is one whose key no provider in the
     *         chain has, or if the provider itself fails
     * @throws IllegalArgumentException if {@code runtimeData} is {@code null}
     */
    @Override
    public InterpolationOutcome outcome(String input, RuntimeData runtimeData) {
        nonNull(input, "Cannot interpolate a null input");
        requireNamespaces(runtimeData);
        var matcher = InterpolationPatterns.groupingPatternForVariable(SECRETS).matcher(input);
        if (!matcher.find()) {
            return new InterpolationOutcome(input, input);
        }
        var key = matcher.group(VARIABLE_GROUP_NAME);
        if (key == null) {
            throw new BratException("The secrets reference '" + input + "' has no key.");
        }
        var optionalValue = provider.getSecret(key);
        if (optionalValue.isEmpty()) {
            throw new BratException(String.format("No value for ${secrets.%s} found.", key));
        }
        return new InterpolationOutcome(optionalValue.get(), input + " → ***", true);
    }
}
