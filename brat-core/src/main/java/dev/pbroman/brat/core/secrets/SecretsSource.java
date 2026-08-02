package dev.pbroman.brat.core.secrets;

import java.util.Map;

import dev.pbroman.brat.core.exception.BratException;
import org.apache.commons.lang3.StringUtils;

import static dev.pbroman.brat.core.util.ExceptionUtils.bratExceptionOnNull;

/**
 * One entry of the ordered list of secrets sources: a type and the parameters identifying that one
 * source, typically the location of a secrets file.
 * <p>
 * A source's position in the list is its priority, and one source yields exactly one provider
 * instance. Unlike connection parameters, these are used as given and are never interpolated.
 *
 * @param type the source type, matching the {@link dev.pbroman.brat.core.api.secrets.SecretsProviderFactory}
 *        that creates providers for it, and the connection whose parameters it binds to
 * @param params the parameters identifying this source, e.g. {@code location}; may be empty
 */
public record SecretsSource(String type, Map<String, String> params) {

    /**
     * Validates the type and copies the parameters, so later modification of the argument does not
     * affect this source.
     *
     * @throws BratException if {@code type} is {@code null} or blank, if {@code params} is
     *         {@code null}, or if {@code params} holds a {@code null} or blank key or a
     *         {@code null} value
     */
    public SecretsSource {
        if (StringUtils.isBlank(type)) {
            throw new BratException("A secrets source type must not be null or blank");
        }
        bratExceptionOnNull(params, "The params of secrets source '" + type + "' must not be null");
        params.forEach((key, value) -> {
            if (StringUtils.isBlank(key) || value == null) {
                throw new BratException("Param keys must not be null or blank and values must not be null,"
                        + " but secrets source '" + type + "' has an invalid entry for key: '" + key + "'");
            }
        });
        params = Map.copyOf(params);
    }
}
