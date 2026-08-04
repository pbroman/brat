package dev.pbroman.brat.core.secrets;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import dev.pbroman.brat.core.api.secrets.SecretsProvider;
import dev.pbroman.brat.core.exception.BratException;
import org.apache.commons.lang3.StringUtils;

/**
 * A {@link SecretsProvider} that resolves a key against an ordered chain of other providers,
 * first hit winning.
 * <p>
 * Position in the chain <em>is</em> priority: there is no separate precedence setting and no way to
 * disable an entry. This is also the type the ordered bootstrap rule is expressed with — a provider
 * whose own configuration references secrets is built against a composite holding only the
 * providers activated before it, which makes that rule structural rather than separately enforced.
 */
public final class CompositeSecretsProvider implements SecretsProvider {

    private final List<SecretsProvider> providers;

    /**
     * Constructs a composite over an ordered chain of providers.
     * <p>
     * The list is copied, so later modification of {@code providers} does not affect this
     * composite. An empty list is valid and yields a composite that resolves nothing.
     *
     * @param providers the providers to consult, in priority order; must not be {@code null} and
     *        must not contain {@code null}
     * @throws BratException if {@code providers} is {@code null} or contains {@code null}
     */
    public CompositeSecretsProvider(List<SecretsProvider> providers) {
        if (providers == null || providers.stream().anyMatch(Objects::isNull)) {
            throw new BratException("The secrets provider list or any of its members must not be null");
        }
        this.providers = List.copyOf(providers);
    }

    /**
     * Resolves {@code key} by consulting each provider in order and returning the first value
     * found. Providers after the one that resolved the key are not consulted.
     *
     * @param key the logical secret name; never {@code null} or blank
     * @return the value from the first provider that has {@code key}, or {@link Optional#empty()}
     *         if no provider in the chain has it — including when the chain is empty
     * @throws BratException if {@code key} is {@code null} or blank, or if any provider consulted
     *         throws it; a provider failure aborts immediately and later providers are not
     *         consulted, since a real failure is not a miss to be fallen through
     */
    @Override
    public Optional<String> getSecret(String key) {
        if (StringUtils.isBlank(key)) {
            throw new BratException("A secrets key must not be null or blank");
        }
        for (SecretsProvider provider : providers) {
            var optionalValue = provider.getSecret(key);
            if (optionalValue.isPresent()) {
                return optionalValue;
            }
        }
        return Optional.empty();
    }

    /**
     * Closes every provider in the chain, in order.
     * <p>
     * Every provider is closed even if an earlier one fails, so one broken provider cannot leak the
     * resources of the others.
     *
     * @throws BratException if closing any provider failed, thrown once all have been closed, with
     *         the first failure as its cause and every subsequent one attached as a suppressed
     *         exception
     */
    @Override
    public void close() {
        var exceptions = new ArrayList<Exception>();
        for (SecretsProvider provider : providers) {
            try {
                provider.close();
            } catch (Exception e) {
                exceptions.add(e);
            }
        }
        if (!exceptions.isEmpty()) {
            var bratException = new BratException(
                    String.format(
                            "%s secrets providers could not be closed, first exception saved as cause.",
                            exceptions.size()),
                    exceptions.getFirst());
            exceptions.stream().skip(1).forEach(bratException::addSuppressed);
            throw bratException;
        }
    }
}
