package dev.pbroman.brat.core.launch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.util.Require;

import static dev.pbroman.brat.core.util.Constants.CONSTANTS;
import static dev.pbroman.brat.core.util.Constants.ENV;
import static dev.pbroman.brat.core.util.Constants.PARAMS;
import static dev.pbroman.brat.core.util.Constants.RESPONSE_VARS;
import static dev.pbroman.brat.core.util.Constants.SECRETS;
import static dev.pbroman.brat.core.util.Constants.VARS;

/**
 * The launch parameters of an environment, routed by their keys: the ordinary parameters, and the
 * values that override a namespace for the run.
 * <p>
 * A parameter whose key starts with a namespace and a dot overrides that namespace's value under the
 * rest of the key — {@code env.baseUrl=http://x} makes {@code ${env.baseUrl}} resolve to
 * {@code http://x} — and every other parameter is an ordinary {@code ${params.x}}. The key alone
 * decides; nothing has to be switched on.
 *
 * @param params the ordinary parameters, read as {@code ${params.x}}; never {@code null}, unmodifiable
 * @param constants values overriding the suite's {@code constants}; never {@code null}, unmodifiable
 * @param env values overriding the environment's {@code env}; never {@code null}, unmodifiable
 * @param vars values the {@code vars} namespace starts the run with; never {@code null},
 *        unmodifiable. A later capture of the same key replaces one, as it replaces any value
 * @param secrets secrets resolved ahead of every source; never {@code null}, unmodifiable
 * @param unrecognised the keys kept as ordinary parameters although they contain a dot — almost always
 *        a mistyped namespace, so worth a warning; never {@code null}, unmodifiable, in the order of
 *        the parameters
 */
public record Overrides(
        Map<String, Object> params,
        Map<String, Object> constants,
        Map<String, Object> env,
        Map<String, Object> vars,
        Map<String, String> secrets,
        List<String> unrecognised) {

    /** The namespaces a key may start with to mean something other than an ordinary parameter. */
    private static final Set<String> ROUTED = Set.of(CONSTANTS, ENV, VARS, SECRETS, PARAMS, RESPONSE_VARS);

    /**
     * Copies every component, so the record cannot be changed through a map it was given.
     *
     * @param params the ordinary parameters
     * @param constants the {@code constants} overrides
     * @param env the {@code env} overrides
     * @param vars the starting {@code vars}
     * @param secrets the secrets overrides
     * @param unrecognised the keys kept as parameters although they contain a dot
     * @throws BratException if any argument is {@code null}
     */
    public Overrides {
        params = copy(params, PARAMS);
        constants = copy(constants, CONSTANTS);
        env = copy(env, ENV);
        vars = copy(vars, VARS);
        Require.nonNull(secrets, "The secrets overrides must not be null");
        secrets = Collections.unmodifiableMap(new LinkedHashMap<>(secrets));
        Require.nonNull(unrecognised, "The unrecognised keys must not be null");
        unrecognised = List.copyOf(unrecognised);
    }

    /**
     * Routes {@code params} by key.
     * <p>
     * A key is split at its <strong>first</strong> dot. Where the part before it names a namespace,
     * the rest — dots and all, so {@code env.db.host} overrides {@code db.host} — is the key in that
     * namespace:
     * <ul>
     *     <li>{@code constants}, {@code env}, {@code vars} — to the map of that name, value unchanged;</li>
     *     <li>{@code secrets} — to {@link #secrets()}, the value as text, a {@code null} value dropped:
     *         a secret with no value is absent;</li>
     *     <li>{@code params} — to {@link #params()} without its prefix, so {@code params.x} and
     *         {@code x} are two spellings of one parameter.</li>
     * </ul>
     * Every other key — one with no dot, or whose first part names no namespace — goes to
     * {@link #params()} as it is, value unchanged; one containing a dot is also listed in
     * {@link #unrecognised()}.
     *
     * @param params the parameters to route; must not be {@code null}. Not modified
     * @return the routed parameters
     * @throws BratException if {@code params} is {@code null}; if a key names {@code responseVars},
     *         which every response replaces, so no launch may override it; if a key is a namespace
     *         and a dot with nothing after it; or if two keys name one parameter, as {@code x} and
     *         {@code params.x} do — each naming the key, never a value
     */
    static Overrides of(Map<String, ?> params) {
        Require.nonNull(params, "The params to route must not be null");
        var plain = new LinkedHashMap<String, Object>();
        var constants = new LinkedHashMap<String, Object>();
        var env = new LinkedHashMap<String, Object>();
        var vars = new LinkedHashMap<String, Object>();
        var secrets = new LinkedHashMap<String, String>();
        var unrecognised = new ArrayList<String>();
        for (var entry : params.entrySet()) {
            var key = entry.getKey();
            var value = entry.getValue();
            var dot = key.indexOf('.');
            var namespace = dot < 0 ? null : key.substring(0, dot);
            if (namespace == null || !ROUTED.contains(namespace)) {
                putParam(plain, key, key, value);
                if (namespace != null) {
                    unrecognised.add(key);
                }
                continue;
            }
            var rest = key.substring(dot + 1);
            if (RESPONSE_VARS.equals(namespace)) {
                throw new BratException("The launch param '" + key + "' cannot override " + RESPONSE_VARS
                        + ": every response replaces it");
            }
            if (rest.isBlank()) {
                throw new BratException(
                        "The launch param '" + key + "' names the namespace '" + namespace + "' but no key in it");
            }
            if (CONSTANTS.equals(namespace)) {
                constants.put(rest, value);
            } else if (ENV.equals(namespace)) {
                env.put(rest, value);
            } else if (VARS.equals(namespace)) {
                vars.put(rest, value);
            } else if (SECRETS.equals(namespace)) {
                if (value != null) {
                    secrets.put(rest, String.valueOf(value));
                }
            } else {
                putParam(plain, rest, key, value);
            }
        }
        return new Overrides(plain, constants, env, vars, secrets, unrecognised);
    }

    /**
     * An unmodifiable copy of {@code values}, which may hold {@code null} values.
     *
     * @param values the map to copy
     * @param namespace what it holds, for the message
     * @return the copy
     * @throws BratException if {@code values} is {@code null}
     */
    private static Map<String, Object> copy(Map<String, Object> values, String namespace) {
        Require.nonNull(values, "The " + namespace + " overrides must not be null");
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    /**
     * Adds an ordinary parameter, rejecting a second spelling of one already added.
     *
     * @param plain the ordinary parameters so far; modified
     * @param name the parameter's name
     * @param key the key it was given under, for the message
     * @param value its value
     * @throws BratException if {@code plain} already holds {@code name}
     */
    private static void putParam(Map<String, Object> plain, String name, String key, Object value) {
        if (plain.containsKey(name)) {
            throw new BratException("The launch param '" + name + "' is given twice: '" + key + "' names it again");
        }
        plain.put(name, value);
    }
}
