package dev.pbroman.brat.core.util;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import dev.pbroman.brat.core.exception.BratException;

/**
 * Static helpers for reading HTTP headers that were authored as a plain map.
 * <p>
 * HTTP header names are case-insensitive per spec, but a suite file's {@code headers:} block is an
 * ordinary YAML mapping, so {@code content-type} and {@code Content-Type} arrive as distinct keys.
 * These helpers make the *lookup* case-insensitive while leaving the map exactly as authored — which
 * is what keeps a report and an interpolation outcome key echoing the author's own spelling.
 * <p>
 * Because two spellings of one header name are distinct YAML keys, the loader's duplicate-key check
 * cannot catch them; {@link #requireNoCaseDuplicates(Map)} is what rejects that, so a lookup never
 * has to choose between two contradictory values.
 */
public final class HttpHeaderUtils {

    private HttpHeaderUtils() {
        // utility class
    }

    /**
     * Looks up a header by name, ignoring case.
     *
     * @param headers the headers as authored, or {@code null}
     * @param name the header name to look for, or {@code null}
     * @return the value of the entry whose key equals {@code name} ignoring case, or {@code null} if
     *         {@code headers} is {@code null} or empty, {@code name} is {@code null}, or no entry
     *         matches. A matching entry with a {@code null} value is indistinguishable from no match,
     *         which is deliberate — both mean "no usable value". Where several entries could match,
     *         the map's iteration order decides; {@link #requireNoCaseDuplicates(Map)} exists so that
     *         case never arises for a header map BRAT built
     */
    public static String get(Map<String, String> headers, String name) {
        return match(headers, name).map(Map.Entry::getValue).orElse(null);
    }

    /**
     * Whether a header is present under any capitalisation.
     *
     * @param headers the headers as authored, or {@code null}
     * @param name the header name to look for, or {@code null}
     * @return {@code true} if an entry's key equals {@code name} ignoring case, whatever its value;
     *         {@code false} if {@code headers} or {@code name} is {@code null}
     */
    public static boolean contains(Map<String, String> headers, String name) {
        return match(headers, name).isPresent();
    }

    /**
     * Requires that no two header names differ only in case.
     * <p>
     * {@code Content-Type} and {@code content-type} are one header to HTTP and two keys to YAML, so
     * a suite declaring both is stating two values for one header. Silently taking either is the kind
     * of quiet wrong result these helpers exist to remove, so it is rejected instead.
     * <p>
     * Names are compared under {@link String#CASE_INSENSITIVE_ORDER}, which agrees with the
     * {@link String#equalsIgnoreCase(String)} that {@link #get(Map, String)} uses, so "the same header
     * name" means one thing in this class rather than two.
     *
     * @param headers the headers as authored, or {@code null}; {@code null} and empty both pass, and
     *        a {@code null} key is ignored rather than compared
     * @throws BratException if two keys are equal ignoring case, naming both spellings, the one
     *         encountered first and then the one that collided with it
     */
    public static void requireNoCaseDuplicates(Map<String, String> headers) {
        if (headers == null) {
            return;
        }
        // Each key maps to itself, so put returns the spelling already seen: a TreeMap keeps the key
        // it was first given and replaces only the value.
        var seen = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
        for (var key : headers.keySet()) {
            if (key == null) {
                continue;
            }
            var previous = seen.put(key, key);
            if (previous != null) {
                throw new BratException(String.format(
                        "Duplicate header name: '%s' and '%s' differ only in case, and HTTP header "
                                + "names are case-insensitive",
                        previous, key));
            }
        }
    }

    /**
     * Finds the entry whose key equals {@code name} ignoring case.
     * <p>
     * The comparison is written with {@code name} as the receiver on purpose:
     * {@link String#equalsIgnoreCase(String)} null-checks its argument but not its receiver, so this
     * ordering tolerates a {@code null} key in the map rather than throwing on one.
     *
     * @param headers the headers as authored, or {@code null}
     * @param name the header name to look for, or {@code null}
     * @return the matching entry, or empty if there is none or either argument is {@code null}
     */
    private static Optional<Map.Entry<String, String>> match(Map<String, String> headers, String name) {
        if (headers == null || name == null) {
            return Optional.empty();
        }
        return headers.entrySet().stream()
                .filter(entry -> name.equalsIgnoreCase(entry.getKey()))
                .findFirst();
    }
}
