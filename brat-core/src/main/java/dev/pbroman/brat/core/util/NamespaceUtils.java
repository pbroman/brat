package dev.pbroman.brat.core.util;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import dev.pbroman.brat.core.exception.BratException;

import static dev.pbroman.brat.core.util.Constants.TOKEN_PREFIX;

/**
 * Prepares the values of a namespace a document supplies — {@code constants}, {@code env},
 * {@code params} — for lookup.
 * <p>
 * A namespace is looked up by its whole dotted key, so {@code ${env.db.host}} asks for the key
 * {@code db.host}. A value written nested has to be stored under that key to be reachable at all, and
 * a value that supplies a namespace is literal: a {@code ${...}} in it would never be resolved, since
 * resolved output is not scanned again.
 */
public final class NamespaceUtils {

    private NamespaceUtils() {
        // utility class
    }

    /**
     * Returns {@code values} flattened to dotted keys, after rejecting anything that would make a
     * lookup silently wrong.
     * <p>
     * <strong>Flattening.</strong> A value that is itself a {@link Map} is replaced by its entries, each
     * under the parent's key, a {@code .} and its own key — to any depth, so
     * {@code db: {primary: {host: x}}} becomes {@code db.primary.host -> x}. A nested key that is not a
     * {@code String} is used by its {@code String.valueOf}, and so is a {@code null} key at any level,
     * which becomes {@code "null"}. A nested map with no entries contributes no
     * key. Every other value is kept as the same instance under its key — a {@link java.util.List}
     * included, together with everything inside it: a map that is an element of a list stays a map,
     * since an element has no key to join.
     * <p>
     * <strong>Order.</strong> The result iterates in the order of {@code values}, a nested map's entries
     * taking its place in that order — for a document, the order the keys were written.
     * <p>
     * <strong>{@code null}.</strong> A {@code null} value is kept, under its key: in a namespace it
     * means <em>absent</em>, which is the lookup's to decide.
     * <p>
     * Keys are otherwise used as given: an empty or blank key is not rejected here.
     *
     * @param values the namespace's values as supplied, possibly nested; must not be {@code null}.
     *        Not modified
     * @param namespace the namespace's name as an author writes it — {@code constants}, {@code env},
     *        {@code params} — used only to name the key in a message; must not be {@code null}
     * @return a new, unmodifiable map with no {@code Map} among its values; empty if {@code values} is
     * @throws BratException if either argument is {@code null}; if flattening yields one key twice — a
     *         nested path colliding with a key written with a dot, as {@code db: {host: x}} does with
     *         {@code "db.host": y}; or if any {@code String} holds <code>${</code>, whether a value, an element
     *         of a list, or anywhere further inside one. Each message names the offending key as
     *         {@code namespace.key} and <strong>never quotes the value</strong>, which may be a
     *         credential. Where several keys offend, which one is reported is undefined
     */
    public static Map<String, Object> flattenLiteral(Map<String, ?> values, String namespace) {
        Require.nonNull(values, "The namespace values must not be null");
        Require.nonNull(namespace, "The namespace must not be null");
        var flattened = new LinkedHashMap<String, Object>();
        flattenInto(values, "", namespace, flattened);
        return Collections.unmodifiableMap(flattened);
    }

    /**
     * Adds every entry of {@code values} to {@code flattened}, each key prefixed with {@code prefix},
     * descending into nested maps.
     *
     * @param values the map to flatten
     * @param prefix the dotted path of {@code values} itself, with its trailing dot; empty at the top
     * @param namespace the namespace's name, for messages
     * @param flattened the result being built; modified
     */
    private static void flattenInto(Map<?, ?> values, String prefix, String namespace, Map<String, Object> flattened) {
        for (var entry : values.entrySet()) {
            var key = prefix + entry.getKey();
            var value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                flattenInto(nested, key + ".", namespace, flattened);
                continue;
            }
            if (holdsToken(value)) {
                throw new BratException(String.format(
                        "'%s.%s' holds a ${...} token, but %s values are literal: nothing in them is resolved",
                        namespace, key, namespace));
            }
            if (flattened.containsKey(key)) {
                throw new BratException(String.format(
                        "'%s.%s' is declared twice: once nested and once as a dotted key", namespace, key));
            }
            flattened.put(key, value);
        }
    }

    /**
     * Whether {@code value} is, or anywhere inside it holds, a {@code String} containing a token prefix.
     *
     * @param value the value to inspect, possibly {@code null}
     * @return {@code true} if a token prefix was found
     */
    private static boolean holdsToken(Object value) {
        return switch (value) {
            case String text -> text.contains(TOKEN_PREFIX);
            case Collection<?> elements -> elements.stream().anyMatch(NamespaceUtils::holdsToken);
            case Map<?, ?> map -> map.values().stream().anyMatch(NamespaceUtils::holdsToken);
            case null, default -> false;
        };
    }
}
