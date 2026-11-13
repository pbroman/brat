package dev.pbroman.brat.core.secrets;

import java.util.Optional;

import dev.pbroman.brat.core.api.reporting.ValueMask;
import dev.pbroman.brat.core.api.secrets.SecretsProvider;
import dev.pbroman.brat.core.exception.BratException;

import static dev.pbroman.brat.core.util.Require.nonNull;

/**
 * A {@link SecretsProvider} that adds every value it returns to a run's {@link ValueMask}, so that the
 * value is masked wherever a report prints it — in a response that echoes it, or in a variable it was
 * copied into.
 * <p>
 * It answers exactly as the provider it wraps. Closing it does nothing: the wrapped provider belongs to
 * whoever built it, and is closed by them.
 */
public final class RecordingSecretsProvider implements SecretsProvider {

    private final SecretsProvider provider;
    private final ValueMask mask;

    /**
     * Constructs a provider recording what {@code provider} returns into {@code mask}.
     *
     * @param provider the provider to answer from; must not be {@code null}
     * @param mask the run's mask, which every returned value is added to as a secret; must not be
     *        {@code null}
     * @throws BratException if {@code provider} or {@code mask} is {@code null}
     */
    public RecordingSecretsProvider(SecretsProvider provider, ValueMask mask) {
        nonNull(provider, "The provider to record must not be null");
        nonNull(mask, "The mask to record into must not be null");
        this.provider = provider;
        this.mask = mask;
    }

    /**
     * Answers as the wrapped provider does, and adds a present value to the mask as a secret before
     * returning it.
     * <p>
     * Whatever the wrapped provider throws propagates unchanged, and nothing is added.
     *
     * @param key the logical secret name, passed on as it is
     * @return what the wrapped provider returned
     */
    @Override
    public Optional<String> getSecret(String key) {
        var value = provider.getSecret(key);
        value.ifPresent(mask::addSecret);
        return value;
    }
}
