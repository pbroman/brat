package dev.pbroman.brat.core.interpolation;

import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import dev.pbroman.brat.core.api.interpolation.BratFunction;
import dev.pbroman.brat.core.exception.BratException;
import lombok.extern.slf4j.Slf4j;
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
 * and needing no change to core. Each function carries its own name
 * ({@link BratFunction#name()}), so a plugin discovered as bare instances registers identically to
 * one wired by hand.
 * <p>
 * This registry is the <strong>single validator</strong> of function names: nothing else checks
 * them, so every implementation reaches the same rules whether it was written as a class, a lambda
 * through {@link BratFunction#of}, or loaded from a jar.
 */
@Slf4j
public final class FunctionRegistry {

    private final Map<String, BratFunction> functions;

    /**
     * Constructs a registry over the functions a run has available.
     * <p>
     * The order of {@code functions} matters only for repeated names: a function whose
     * {@link BratFunction#name()} exactly repeats an earlier one <strong>replaces</strong> it, and
     * the replacement is logged at WARN describing both functions. That is how a plugin
     * overrides a standard function, so the caller controls the order — a consumer offering
     * overrides puts {@code StandardFunctions.all()} first and contributed functions after it.
     * <p>
     * Two names that resolve to the same lookup key without being identical ({@code uuid} and
     * {@code UUID}) are an error rather than an override. An exact repeat is evidence of intent —
     * whoever wrote it meant that function; a differing spelling is evidence of unawareness, since
     * anyone meaning to override would have spelled the name the same way.
     *
     * @param functions the functions a run has available, written without the {@code __} prefix, in
     *        registration order; may be empty, which is a registry that recognises nothing. Not
     *        retained — a later change to the collection does not affect the registry
     * @throws BratException if {@code functions} is {@code null}, if it holds a {@code null}
     *         element, if a {@link BratFunction#name()} is {@code null} or blank, if a name carries
     *         the {@code __} prefix (it is the syntax, not part of the name), or if two names differ
     *         only in case
     */
    public FunctionRegistry(Collection<BratFunction> functions) {
        nonNull(functions, "The functions of a registry must be set");
        var byLookupKey = new HashMap<String, BratFunction>();
        var spellingByLookupKey = new HashMap<String, String>();
        for (var function : functions) {
            nonNull(function, "A function of a registry must not be null");
            var name = function.name();
            if (StringUtils.isBlank(name)) {
                throw new BratException("A function name must not be null or blank");
            }
            if (name.startsWith(FUNCTION_PREFIX)) {
                throw new BratException("The function name '" + name + "' must not carry the '" + FUNCTION_PREFIX
                        + "' prefix, which is syntax rather than part of the name");
            }
            var key = name.toLowerCase(Locale.ROOT);
            var previousSpelling = spellingByLookupKey.put(key, name);
            if (previousSpelling != null && !previousSpelling.equals(name)) {
                throw new BratException("The function names '" + previousSpelling + "' and '" + name
                        + "' differ only in case and both resolve to '" + key
                        + "'. Lookup is case-insensitive, so only one could ever be reached");
            }
            var replaced = byLookupKey.put(key, function);
            if (replaced != null) {
                log.warn("Two functions are named '{}': {} replaces {}. The later one wins", name, function, replaced);
            }
        }
        this.functions = Map.copyOf(byLookupKey);
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
        nonNull(function, "There is no function named '" + name + "'");
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
