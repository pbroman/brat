package dev.pbroman.brat.core.api.secrets;

import java.util.Optional;

import dev.pbroman.brat.core.exception.BratException;

/**
 * A source of secret values, resolving a logical secret name to its value.
 * <p>
 * A test suite only ever references a logical name (e.g. {@code ${secrets.apiKey}}) and never
 * names the source it comes from; mapping that name onto a physical location — an environment
 * variable, a file entry, a path in a secrets backend — is entirely this interface's concern.
 * <p>
 * Implementations distinguish two outcomes that must not be conflated: <em>this provider does not
 * have the key</em>, reported as an empty {@link Optional} so the next provider in the chain is
 * consulted, and <em>this provider failed</em>, reported as a {@link BratException} that aborts
 * the run.
 */
public interface SecretsProvider extends AutoCloseable {

    /**
     * Resolves a logical secret name to its value.
     *
     * @param key the logical secret name, e.g. {@code "apiKey"}; never {@code null} or blank
     * @return the secret value if this provider has {@code key}, or {@link Optional#empty()} if it
     *         does not — an empty result means "not here, try the next provider", never an error
     * @throws BratException if {@code key} is {@code null} or blank, or if the provider fails for
     *         a real reason (backend unreachable, authentication rejected, decryption failed,
     *         malformed source document) — never merely because {@code key} is absent
     */
    Optional<String> getSecret(String key);

    /**
     * Releases any resources this provider holds (open connections, cached decrypted material).
     * Does nothing by default, which is correct for providers holding no resources.
     * <p>
     * Narrows {@link AutoCloseable#close()} to throw no checked exception, per the convention that
     * plugin interfaces declare none.
     *
     * @throws BratException if releasing a resource fails
     */
    @Override
    default void close() {
        // no resources to release by default
    }
}
