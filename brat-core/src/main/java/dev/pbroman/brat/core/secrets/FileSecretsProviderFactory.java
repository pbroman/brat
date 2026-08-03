package dev.pbroman.brat.core.secrets;

import java.util.HashMap;
import java.util.Map;

import dev.pbroman.brat.core.api.secrets.SecretsProvider;
import dev.pbroman.brat.core.api.secrets.SecretsProviderFactory;
import dev.pbroman.brat.core.exception.BratException;
import org.apache.commons.lang3.StringUtils;

import static dev.pbroman.brat.core.util.Require.nonNull;
import static dev.pbroman.brat.core.util.ResourceReader.readFileToString;

/**
 * Creates providers for plaintext YAML secrets files.
 * <p>
 * The file is read from the location given as a parameter, flattened to dotted keys, and served
 * from memory, so the file is read once when the chain is built rather than per lookup.
 */
public final class FileSecretsProviderFactory implements SecretsProviderFactory {

    /**
     * The source type this factory serves.
     */
    public static final String TYPE = "file";

    /**
     * The parameter naming the file to read.
     */
    public static final String LOCATION_PARAM = "location";

    /**
     * The key a secrets file declares its own type with, which is configuration rather than a
     * secret and is therefore not exposed as one.
     */
    public static final String TYPE_KEY = "type";

    @Override
    public String type() {
        return TYPE;
    }

    /**
     * Creates a provider serving the entries of the file at {@code params.get("location")}.
     * <p>
     * The location is a resource location as {@link dev.pbroman.brat.core.util.ResourceReader}
     * resolves it. A {@code type} entry in the file is removed rather than served, so a file
     * declaring {@code type: file} does not expose a secret named {@code type}. Any other parameter
     * is ignored.
     *
     * @param params the resolved parameters; must hold {@code location}
     * @return a provider over the file's entries, resolving nothing if the file holds no entries
     * @throws BratException if {@code params} is {@code null}, if it has no {@code location} entry
     *         or a blank one, if the file cannot be read, or if its content is not a document
     *         {@link FlatYamlLoader} accepts
     */
    @Override
    public SecretsProvider create(Map<String, String> params) {
        nonNull(params, "params may not be null");
        var location = params.get(LOCATION_PARAM);
        if (StringUtils.isBlank(location)) {
            throw new BratException("The params must contain a valid " + LOCATION_PARAM);
        }
        var map = new HashMap<>(FlatYamlLoader.load(readFileToString(location)));
        map.remove(TYPE_KEY);
        return new MapSecretsProvider(map);
    }
}
