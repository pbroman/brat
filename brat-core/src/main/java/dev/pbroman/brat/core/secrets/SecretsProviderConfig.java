package dev.pbroman.brat.core.secrets;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import dev.pbroman.brat.core.exception.BratException;
import org.apache.commons.lang3.StringUtils;

import static dev.pbroman.brat.core.util.ExceptionUtils.bratExceptionOnNull;

/**
 * The configuration a chain of secrets providers is built from: the parameters each provider type
 * is configured with, and the ordered list of sources that decides which providers actually exist.
 * <p>
 * The two halves have different lifetimes and different origins. Parameters are stable and are read
 * from a configuration document; sources are chosen per run, and are produced by whatever launches
 * the run rather than authored in that document. Parameters are inert until a source of their type
 * activates them, so configuring a type nothing uses costs nothing.
 * <p>
 * Any type may be configured, not only one reaching a backend over a network: a Vault address, a
 * plaintext file's default charset and the environment-variable prefix all live here, and a type
 * needing nothing at all is simply absent. Every source of a type is built from the same parameters,
 * with its own overriding them where they overlap.
 * <p>
 * Values are held exactly as written, {@code ${…}} tokens included: they are interpolated while the
 * chain is being built, not while it is being read.
 *
 * @param providerParams the parameters by provider type, each a flat map of dotted keys
 * @param sources the sources to build providers from, in priority order; may be empty, which yields
 *        a chain of nothing but the always-present environment-variable provider
 */
public record SecretsProviderConfig(Map<String, Map<String, String>> providerParams, List<SecretsSource> sources) {

    /**
     * Validates and copies both arguments, so later modification of either does not affect this
     * configuration.
     *
     * @throws BratException if either argument is {@code null}, if a provider type is {@code null}
     *         or blank, if a type's parameter map is {@code null} or holds a {@code null} or blank
     *         key or a {@code null} value, or if {@code sources} holds a {@code null} element
     */
    public SecretsProviderConfig {
        bratExceptionOnNull(providerParams, "The provider params must not be null");
        bratExceptionOnNull(sources, "The sources must not be null");
        providerParams.forEach((type, params) -> {
            if (StringUtils.isBlank(type)) {
                throw new BratException("A provider type must not be null or blank");
            }
            bratExceptionOnNull(params, "The params of provider type '" + type + "' must not be null");
            params.forEach((key, value) -> {
                if (StringUtils.isBlank(key) || value == null) {
                    throw new BratException("Param keys must not be null or blank and values must not be null,"
                            + " but provider type '" + type + "' has an invalid entry for key: '" + key + "'");
                }
            });
        });
        if (sources.stream().anyMatch(Objects::isNull)) {
            throw new BratException("The sources must not contain null");
        }
        providerParams = providerParams.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> Map.copyOf(entry.getValue())));
        sources = List.copyOf(sources);
    }

    /**
     * Returns the parameters every source of {@code type} is built from.
     *
     * @param type the provider type
     * @return the parameters configured for {@code type}, or an empty map if none are — which is
     *         valid for a type needing no configuration of its own
     * @throws BratException if {@code type} is {@code null} or blank
     */
    public Map<String, String> paramsFor(String type) {
        if (StringUtils.isBlank(type)) {
            throw new BratException("A provider type must not be null or blank");
        }
        return providerParams.getOrDefault(type, Map.of());
    }

    /**
     * Returns a copy of this configuration with different sources.
     *
     * @param newSources the sources of the returned configuration, in priority order
     * @return a new configuration with this one's provider params and {@code newSources}; this
     *         configuration is unchanged
     * @throws BratException if {@code newSources} is {@code null} or holds a {@code null} element
     */
    public SecretsProviderConfig withSources(List<SecretsSource> newSources) {
        return new SecretsProviderConfig(providerParams, newSources);
    }
}
