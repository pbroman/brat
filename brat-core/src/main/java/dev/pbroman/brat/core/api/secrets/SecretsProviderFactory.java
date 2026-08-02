package dev.pbroman.brat.core.api.secrets;

import java.util.Map;

import dev.pbroman.brat.core.exception.BratException;

/**
 * Creates a {@link SecretsProvider} of one type from that type's parameters.
 * <p>
 * A provider needs to know which file to read or which backend to reach, which a no-argument
 * constructor cannot express; this interface is that seam. {@link #type()} matches the type declared
 * by a secrets source, and {@link #create(Map)} binds the parameters resolved for it into a live
 * provider. Implementations are stateless and shared for the whole run, while the providers they
 * create are per-run instances.
 * <p>
 * Parameters arrive <strong>fully interpolated</strong>. An implementation must never interpolate
 * anything itself: the rule restricting which secrets a provider's own configuration may resolve is
 * applied while those parameters are being resolved, and interpolating later would bypass it.
 */
public interface SecretsProviderFactory {

    /**
     * The source type this factory creates providers for, e.g. {@code "file"} or {@code "vault"}.
     *
     * @return the type name; never {@code null} or blank, and constant for the life of the factory
     */
    String type();

    /**
     * Creates a provider from the parameters resolved for one secrets source.
     * <p>
     * Keys are flat and dotted, as they were written in the configuration document — a nested
     * {@code auth: {method: approle}} arrives as {@code auth.method}. Every value is already
     * interpolated, and values may be secret, so an implementation must keep them out of logs and
     * exception messages.
     *
     * @param params the resolved parameters for this source; never {@code null}, possibly empty for
     *        a type needing no configuration, and holding neither {@code null} keys nor
     *        {@code null} values
     * @return a provider bound to {@code params}; never {@code null}
     * @throws BratException if {@code params} is {@code null}, if a parameter this type requires is
     *         missing or unusable, or if creating the provider fails
     */
    SecretsProvider create(Map<String, String> params);
}
