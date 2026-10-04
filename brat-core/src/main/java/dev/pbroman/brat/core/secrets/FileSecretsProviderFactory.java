package dev.pbroman.brat.core.secrets;

import java.util.HashMap;
import java.util.Map;

import dev.pbroman.brat.core.api.secrets.SecretsProvider;
import dev.pbroman.brat.core.api.secrets.SecretsProviderFactory;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.util.NamespaceUtils;

import static dev.pbroman.brat.core.util.Constants.SECRETS;
import static dev.pbroman.brat.core.util.Require.nonBlank;
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
     * <p>
     * <strong>The file is literal.</strong> A secrets file supplies values, and nothing in it is
     * resolved — so a value holding a {@code ${...}} token, which could only ever be served as that
     * text, is refused rather than served. The {@code type} entry is checked like any other.
     *
     * @param params the resolved parameters; must hold {@code location}
     * @return a provider over the file's entries, resolving nothing if the file holds no entries
     * @throws BratException if {@code params} is {@code null}, if it has no {@code location} entry
     *         or a blank one, if the file cannot be read, if its content is not a document
     *         {@link FlatYamlLoader} accepts, or if any of its values holds a {@code ${...}} token —
     *         the message then names the location and the key, as {@code secrets.<key>}, and
     *         <strong>never the value</strong>
     */
    @Override
    public SecretsProvider create(Map<String, String> params) {
        nonNull(params, "params may not be null");
        var location = params.get(LOCATION_PARAM);
        nonBlank(location, "The params must contain a valid " + LOCATION_PARAM);
        var entries = FlatYamlLoader.load(readFileToString(location));
        try {
            // Already flat; called for its literal check, so the rule and the message stay those of
            // every other namespace.
            NamespaceUtils.flattenLiteral(entries, SECRETS);
        } catch (BratException e) {
            throw new BratException("The secrets file '" + location + "' is not usable: " + e.getMessage(), e);
        }
        var map = new HashMap<>(entries);
        map.remove(TYPE_KEY);
        return new MapSecretsProvider(map);
    }
}
