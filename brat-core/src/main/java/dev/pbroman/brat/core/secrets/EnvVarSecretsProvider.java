package dev.pbroman.brat.core.secrets;

import java.util.Optional;
import java.util.function.UnaryOperator;

import dev.pbroman.brat.core.api.secrets.SecretsProvider;
import org.apache.commons.lang3.StringUtils;

/**
 * A {@link SecretsProvider} backed by operating-system environment variables.
 * <p>
 * A logical key is read from the variable {@link SecretsUtils#envVarName} names, so {@code apiKey}
 * is read from {@code BRAT_SECRET_API_KEY}. This is the one provider that needs no configuration
 * document and no backend, which is why it is the always-on default of the provider chain.
 * <p>
 * A variable that is set but empty counts as absent, so the chain falls through to the next
 * provider rather than resolving a credential to the empty string — an empty variable is far more
 * often an unset CI variable that expanded to nothing than a deliberately empty secret.
 */
final class EnvVarSecretsProvider implements SecretsProvider {

    private final String prefix;

    private final UnaryOperator<String> lookup;

    /**
     * Constructs a provider reading environment variables through {@code lookup}.
     *
     * @param prefix the prefix applied to the transformed key; {@code null} is treated as no prefix
     * @param lookup reads one environment variable by name, {@code System::getenv} outside tests
     */
    EnvVarSecretsProvider(String prefix, UnaryOperator<String> lookup) {
        this.prefix = StringUtils.defaultString(prefix);
        this.lookup = lookup;
    }

    @Override
    public Optional<String> getSecret(String key) {
        var value = lookup.apply(SecretsUtils.envVarName(prefix, key));
        return StringUtils.isEmpty(value) ? Optional.empty() : Optional.of(value);
    }
}
