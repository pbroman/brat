package dev.pbroman.brat.core.secrets;

import java.util.Optional;
import java.util.function.UnaryOperator;

import dev.pbroman.brat.core.api.secrets.SecretsProvider;
import dev.pbroman.brat.core.exception.BratException;
import org.apache.commons.lang3.StringUtils;

/**
 * A {@link SecretsProvider} backed by operating-system environment variables.
 * <p>
 * A logical key is transformed into an environment variable name by upper-snake-casing it and
 * applying a prefix, so {@code apiKey} is read from {@code BRAT_SECRET_API_KEY}. This is the one
 * provider that needs no configuration document and no backend, which is why it is the always-on
 * default of the provider chain.
 * <p>
 * A variable that is set but empty counts as absent, so the chain falls through to the next
 * provider rather than resolving a credential to the empty string — an empty variable is far more
 * often an unset CI variable that expanded to nothing than a deliberately empty secret.
 */
public final class EnvVarSecretsProvider implements SecretsProvider {

    /**
     * The prefix applied when none is given.
     */
    public static final String DEFAULT_PREFIX = "BRAT_SECRET_";

    private final String prefix;

    private final UnaryOperator<String> lookup;

    /**
     * Constructs a provider reading from the process environment with {@link #DEFAULT_PREFIX}.
     */
    public EnvVarSecretsProvider() {
        this(DEFAULT_PREFIX);
    }

    /**
     * Constructs a provider reading from the process environment with a given prefix.
     *
     * @param prefix the prefix applied to the transformed key; {@code null} is treated as no prefix
     */
    public EnvVarSecretsProvider(String prefix) {
        this(prefix, System::getenv);
    }

    EnvVarSecretsProvider(String prefix, UnaryOperator<String> lookup) {
        this.prefix = StringUtils.defaultString(prefix);
        this.lookup = lookup;
    }

    @Override
    public Optional<String> getSecret(String key) {
        if (StringUtils.isBlank(key)) {
            throw new BratException("The secret key must not be null or blank");
        }
        var value = lookup.apply(prefix + toUpperSnakeCase(key));
        return StringUtils.isEmpty(value) ? Optional.empty() : Optional.of(value);
    }

    /**
     * Converts a logical key to the upper-snake-case form used in environment variable names.
     * <p>
     * A separator is inserted before an upper-case character when the preceding character is
     * lower-case or a digit, or when the preceding character is upper-case and the following one is
     * lower-case — so an acronym stays intact ({@code apiURL} becomes {@code API_URL}, not
     * {@code API_U_R_L}). Dots become separators, making the flattened key {@code db.password} and
     * the camel-case key {@code dbPassword} deliberately equivalent.
     *
     * @param key the logical key
     * @return the upper-snake-case form, unchanged if {@code key} is already in that form
     */
    private static String toUpperSnakeCase(String key) {
        var result = new StringBuilder(key.length() + 8);
        for (var i = 0; i < key.length(); i++) {
            var current = key.charAt(i);
            if (current == '.') {
                result.append('_');
            } else {
                if (i > 0 && Character.isUpperCase(current) && needsSeparator(key, i)) {
                    result.append('_');
                }
                result.append(Character.toUpperCase(current));
            }
        }
        return result.toString();
    }

    private static boolean needsSeparator(String key, int index) {
        var previous = key.charAt(index - 1);
        if (Character.isLowerCase(previous) || Character.isDigit(previous)) {
            return true;
        }
        return Character.isUpperCase(previous)
                && index + 1 < key.length()
                && Character.isLowerCase(key.charAt(index + 1));
    }
}
