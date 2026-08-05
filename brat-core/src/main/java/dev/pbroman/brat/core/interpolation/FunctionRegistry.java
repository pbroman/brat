package dev.pbroman.brat.core.interpolation;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import dev.pbroman.brat.core.api.interpolation.BratFunction;
import dev.pbroman.brat.core.exception.BratException;
import org.apache.commons.lang3.StringUtils;

import static dev.pbroman.brat.core.interpolation.InterpolationPatterns.FUNCTION_PREFIX;
import static dev.pbroman.brat.core.util.Require.nonNull;

/**
 * The functions available to a run, by name.
 * <p>
 * Names are matched case-insensitively and without the {@code __} prefix, so a registry holds
 * {@code uuid} and answers {@code ${__uuid}}, {@code ${__UUID}} and {@code ${__Uuid}} alike.
 * <p>
 * A plugin adds functions by supplying them here — the same shape as adding a rule to a dispatcher,
 * and needing no change to core.
 */
public final class FunctionRegistry {

    private final Map<String, BratFunction> functions;

    /**
     * Constructs a registry over the functions a run has available.
     *
     * @param functions the functions by name, written without the {@code __} prefix; may be empty,
     *        which is a registry that recognises nothing
     * @throws BratException if {@code functions} is {@code null}, if a name is {@code null} or
     *         blank, if a name carries the {@code __} prefix (it is the syntax, not part of the
     *         name), if a function is {@code null}, or if two names differ only in case
     */
    public FunctionRegistry(Map<String, BratFunction> functions) {
        nonNull(functions, "The functions of a registry must be set");
        var byLowerCaseName = new HashMap<String, BratFunction>();
        for (var entry : functions.entrySet()) {
            var name = entry.getKey();
            if (StringUtils.isBlank(name)) {
                throw new BratException("A function name must not be null or blank");
            }
            if (name.startsWith(FUNCTION_PREFIX)) {
                throw new BratException("The function name '" + name + "' must not carry the '" + FUNCTION_PREFIX
                        + "' prefix, which is syntax rather than part of the name");
            }
            nonNull(entry.getValue(), "The function '" + name + "' must not be null");
            var key = name.toLowerCase(Locale.ROOT);
            if (byLowerCaseName.put(key, entry.getValue()) != null) {
                // Named by the lookup key rather than by the offending spelling: the map's iteration
                // order decides which of the two is seen second, and for Map.of that varies per JVM.
                throw new BratException("Two function names differ only in case and both resolve to '" + key
                        + "'. Lookup is case-insensitive, so only one could ever be reached");
            }
        }
        this.functions = Map.copyOf(byLowerCaseName);
    }

    /**
     * Looks up a function by the name written in a token.
     *
     * @param name the function name, without the {@code __} prefix; matched case-insensitively
     * @return the function registered under {@code name}
     * @throws BratException if {@code name} is {@code null} or blank, or if no function is
     *         registered under it — an unknown function is an error rather than a pass-through,
     *         since {@code ${__uid}} is far likelier to be a typo than literal text
     */
    public BratFunction get(String name) {
        var function = functions.get(requireName(name));
        if (function == null) {
            throw new BratException("There is no function named '" + name + "'");
        }
        return function;
    }

    /**
     * Whether a function is registered under {@code name}.
     *
     * @param name the function name, without the {@code __} prefix; matched case-insensitively
     * @return whether the function exists
     * @throws BratException if {@code name} is {@code null} or blank
     */
    public boolean has(String name) {
        return functions.containsKey(requireName(name));
    }

    /**
     * The lookup key for {@code name}: the name lower-cased, since matching is case-insensitive.
     */
    private static String requireName(String name) {
        if (StringUtils.isBlank(name)) {
            throw new BratException("A function name must not be null or blank");
        }
        return name.toLowerCase(Locale.ROOT);
    }
}
