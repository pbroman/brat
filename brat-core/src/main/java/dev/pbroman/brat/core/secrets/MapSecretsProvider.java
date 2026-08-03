package dev.pbroman.brat.core.secrets;

import java.util.Map;
import java.util.Optional;

import dev.pbroman.brat.core.api.secrets.SecretsProvider;
import dev.pbroman.brat.core.exception.BratException;
import org.apache.commons.lang3.StringUtils;

import static dev.pbroman.brat.core.util.Require.nonNull;

/**
 * A {@link SecretsProvider} backed by an in-memory map of logical key to value.
 * <p>
 * This is the provider behind any source whose secrets arrive as a document rather than a live
 * backend — a plaintext YAML file being the first such source. Turning that document into a map is
 * the caller's job, so this provider is indifferent to where the values came from and needs no
 * filesystem access of its own.
 * <p>
 * Lookup is by exact key, with none of the relaxed matching {@link EnvVarSecretsProvider} applies:
 * {@code dbPassword} and {@code db.password} are different keys here.
 */
public final class MapSecretsProvider implements SecretsProvider {

    private final Map<String, String> secrets;

    /**
     * Constructs a provider over a map of secrets.
     * <p>
     * The map is copied, so later modification of {@code secrets} does not affect this provider. An
     * empty map is valid and yields a provider that resolves nothing.
     *
     * @param secrets the secrets by logical key; must not be {@code null}, and must contain neither
     *        {@code null} keys, blank keys, nor {@code null} values
     * @throws BratException if {@code secrets} is {@code null}, or holds a {@code null} or blank key
     *         or a {@code null} value
     */
    public MapSecretsProvider(Map<String, String> secrets) {
        nonNull(secrets, "The secrets map may not be null.");
        for (Map.Entry<String, String> entry : secrets.entrySet()) {
            if (StringUtils.isBlank(entry.getKey()) || entry.getValue() == null) {
                throw new BratException("Keys may not be null or blank; values may not be null. Invalid state for key: '" + entry.getKey() + "'");
            }
        }
        this.secrets = Map.copyOf(secrets);
    }

    /**
     * Resolves {@code key} by exact lookup in the map this provider was constructed with.
     *
     * @param key the logical secret name; never {@code null} or blank
     * @return the mapped value, or {@link Optional#empty()} if the map has no entry for {@code key};
     *         a mapped empty string resolves to that empty string rather than counting as absent,
     *         since an explicitly empty entry in a document is deliberate
     * @throws BratException if {@code key} is {@code null} or blank
     */
    @Override
    public Optional<String> getSecret(String key) {
        nonNull(key , "The secrets key may not be null.");
        if (StringUtils.isBlank(key)) {
            throw new BratException("The secrets key may not be blank.");
        }
        return Optional.ofNullable(secrets.get(key));
    }
}
