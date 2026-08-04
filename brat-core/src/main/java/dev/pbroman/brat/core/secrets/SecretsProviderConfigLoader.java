package dev.pbroman.brat.core.secrets;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.exception.BratException;
import org.apache.commons.lang3.StringUtils;

/**
 * Reads a secrets provider configuration document into a {@link SecretsProviderConfig}.
 * <p>
 * The document holds a single {@code providers:} section, mapping a provider type to the parameters
 * every source of that type is built from:
 * <pre>
 * providers:
 *   vault:
 *     address: https://vault.example.com
 *     auth.method: approle
 *   sysenv:
 *     prefix: BRAT_SECRET_
 * </pre>
 * Parameters are flattened to dotted keys, so writing {@code auth: {method: approle}} nested is
 * equivalent to writing {@code auth.method} directly. Values are kept as written, with any
 * {@code ${…}} tokens left unresolved for the chain builder to interpolate.
 * <p>
 * The document declares provider parameters only; sources come from elsewhere, so what this returns
 * always has an empty source list. Failures never quote the document, since its values may resolve to
 * secrets.
 * <p>
 * A provider type must not contain a dot. Flattening cannot tell one from a parameter key, so
 * {@code my.vault: {address: …}} is read as the type {@code my} with the parameter
 * {@code vault.address} rather than being rejected.
 */
public final class SecretsProviderConfigLoader {

    /**
     * The document's only section.
     */
    public static final String PROVIDERS_SECTION = "providers";

    private SecretsProviderConfigLoader() {
        // utility class
    }

    /**
     * Reads {@code yaml} into a configuration holding its provider parameters and no sources.
     *
     * @param yaml the document content; a document that is empty, blank or comments only yields a
     *        configuration with no provider params, as does one whose section is an explicitly empty
     *        mapping ({@code providers: {} })
     * @return the configuration; its {@link SecretsProviderConfig#sources()} is always empty
     * @throws BratException if {@code yaml} is {@code null}; if it is not a well-formed YAML mapping
     *         document; if it has any top-level key other than {@code providers}; if
     *         {@code providers} is a scalar rather than a mapping; if a type has no
     *         parameters, i.e. {@code providers.vault} is a scalar rather than a mapping; or
     *         for any reason {@link FlatYamlLoader} rejects the document, which includes a
     *         {@code providers:} key left empty rather than written as an empty mapping
     */
    public static SecretsProviderConfig load(String yaml) {
        var providerParams = new LinkedHashMap<String, Map<String, String>>();
        for (Map.Entry<String, String> entry : FlatYamlLoader.load(yaml).entrySet()) {
            var path = entry.getKey();
            if (PROVIDERS_SECTION.equals(path)) {
                throw new BratException(
                        "The '" + PROVIDERS_SECTION + "' section must be a mapping of provider type to parameters");
            }
            if (!path.startsWith(PROVIDERS_SECTION + ".")) {
                throw new BratException("The only supported top-level key is '" + PROVIDERS_SECTION + "', but found '"
                        + StringUtils.substringBefore(path, ".") + "'");
            }
            var typeAndParam = StringUtils.substringAfter(path, ".");
            var param = StringUtils.substringAfter(typeAndParam, ".");
            if (param.isEmpty()) {
                throw new BratException("The provider type '" + typeAndParam
                        + "' has no parameters, so it must be written as a mapping");
            }
            providerParams
                    .computeIfAbsent(StringUtils.substringBefore(typeAndParam, "."), type -> new LinkedHashMap<>())
                    .put(param, entry.getValue());
        }
        return new SecretsProviderConfig(providerParams, List.of());
    }
}
