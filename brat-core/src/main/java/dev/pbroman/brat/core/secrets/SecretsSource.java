package dev.pbroman.brat.core.secrets;

import java.util.Map;

import dev.pbroman.brat.core.api.secrets.SecretsProviderFactory;
import dev.pbroman.brat.core.exception.BratException;
import org.apache.commons.lang3.StringUtils;

import static dev.pbroman.brat.core.util.Require.nonBlank;
import static dev.pbroman.brat.core.util.Require.nonNull;

/**
 * One entry of the ordered list of secrets sources: a type and the arguments identifying that one
 * source, typically the location of a secrets file.
 * <p>
 * A source's position in the list is its priority, and one source yields exactly one provider
 * instance. Unlike connection arguments, these are used as given and are never interpolated.
 *
 * @param type the source type, matching the {@link SecretsProviderFactory}
 *        that creates providers for it, and the connection whose arguments it binds to
 * @param args the arguments identifying this source, e.g. {@code location}; may be empty
 */
public record SecretsSource(String type, Map<String, String> args) {

    /**
     * Validates the type and copies the arguments, so later modification of the argument does not
     * affect this source.
     *
     * @throws BratException if {@code type} is {@code null} or blank, if {@code args} is
     *         {@code null}, or if {@code args} holds a {@code null} or blank key or a
     *         {@code null} value
     */
    public SecretsSource {
        nonBlank(type, "A secrets source type must not be null or blank");
        nonNull(args, "The args of secrets source '" + type + "' must not be null");
        for (var entry : args.entrySet()) {
            if (StringUtils.isBlank(entry.getKey()) || entry.getValue() == null) {
                throw new BratException("Param keys must not be null or blank and values must not be null,"
                        + " but secrets source '" + type + "' has an invalid entry for key: '"
                        + entry.getKey() + "'");
            }
        }
        args = Map.copyOf(args);
    }
}
