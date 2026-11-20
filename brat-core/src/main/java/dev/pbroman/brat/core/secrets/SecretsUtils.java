package dev.pbroman.brat.core.secrets;

import dev.pbroman.brat.core.util.Require;
import org.apache.commons.lang3.StringUtils;

/**
 * Static helpers for naming secrets outside a suite: the environment variable a secret key is read
 * from.
 */
public final class SecretsUtils {

    /**
     * The prefix of a secret's environment variable when the provider configuration names none.
     */
    public static final String DEFAULT_ENV_PREFIX = "BRAT_SECRET_";

    private SecretsUtils() {
        // utility class
    }

    /**
     * The name of the environment variable a secret key is read from: {@code prefix} followed by the key
     * in upper snake case, so {@code clientSecret} with {@link #DEFAULT_ENV_PREFIX} is
     * {@code BRAT_SECRET_CLIENT_SECRET}.
     * <p>
     * A separator goes before an upper-case character when the character before it is lower-case or a
     * digit, or when that one is upper-case and the next is lower-case — so an acronym stays intact
     * ({@code apiURL} becomes {@code API_URL}, not {@code API_U_R_L}). Dots and hyphens become
     * separators, making {@code db.password}, {@code db-password} and {@code dbPassword} the same name,
     * and every name one a shell can export, which a {@code -} is not. A key already in upper snake case
     * is kept as it is.
     *
     * @param prefix put in front of the converted key; {@code null} is treated as no prefix
     * @param key the secret key; must not be {@code null} or blank
     * @return the variable name
     * @throws dev.pbroman.brat.core.exception.BratException if {@code key} is {@code null} or blank
     */
    public static String envVarName(String prefix, String key) {
        Require.nonBlank(key, "The secret key must not be null or blank");
        return StringUtils.defaultString(prefix) + toUpperSnakeCase(key);
    }

    private static String toUpperSnakeCase(String key) {
        var result = new StringBuilder(key.length() + 8);
        for (var i = 0; i < key.length(); i++) {
            var current = key.charAt(i);
            if (current == '.' || current == '-') {
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
