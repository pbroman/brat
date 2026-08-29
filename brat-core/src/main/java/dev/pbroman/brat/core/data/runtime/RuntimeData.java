package dev.pbroman.brat.core.data.runtime;

import java.util.HashMap;
import java.util.Map;

import dev.pbroman.brat.core.util.Require;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import static dev.pbroman.brat.core.util.Constants.CONSTANTS;
import static dev.pbroman.brat.core.util.Constants.ENV;
import static dev.pbroman.brat.core.util.Constants.MISC;
import static dev.pbroman.brat.core.util.Constants.PARAMS;
import static dev.pbroman.brat.core.util.Constants.RESPONSE_VARS;
import static dev.pbroman.brat.core.util.Constants.VARS;

/**
 * Holds every namespace of values ({@code constants}/{@code env}/{@code vars}/
 * {@code responseVars}/{@code params}/{@code misc}) that interpolation resolves
 * {@code ${namespace.key}} tokens against during a single test suite run.
 */
@Getter
public class RuntimeData {

    private final Map<String, Map<String, Object>> data;

    @Setter
    private String currentPath;

    @Setter
    private int currentRequestNo;

    /**
     * Keyed by variable name; see {@link #captureFailed(String, String)}. No generated accessor: the
     * map is reached through {@link #getTombstone(String)}, so nothing outside can add one.
     */
    @Getter(AccessLevel.NONE)
    private final Map<String, CaptureTombstone> tombstones = new HashMap<>();

    /**
     * Equivalent to {@link #RuntimeData(Map, Map, Map, Map)} with {@code vars} and {@code params}
     * defaulted to empty, mutable maps.
     *
     * @param constants the constants
     * @param env the environment values
     */
    public RuntimeData(Map<String, Object> constants, Map<String, Object> env) {
        this(constants, env, new HashMap<>(), new HashMap<>());
    }

    /**
     * Equivalent to {@link #RuntimeData(Map, Map, Map, Map)} with {@code params} defaulted to an
     * empty, mutable map.
     *
     * @param constants the constants
     * @param env the environment values
     * @param vars the runtime-mutable variables
     */
    public RuntimeData(Map<String, Object> constants, Map<String, Object> env, Map<String, Object> vars) {
        this(constants, env, vars, new HashMap<>());
    }

    /**
     * Constructs the namespaces that exist before anything has run.
     * <p>
     * <strong>{@code responseVars} is not among them, deliberately.</strong> It starts empty and is
     * only ever written by {@link #setResponseVars(Map)} and {@link #clearResponseVars()}, because at
     * construction there is no previous request and therefore no response to supply — unlike
     * {@code constants}, {@code env} and {@code params}, which genuinely are initial state. A test
     * needing a previous response calls {@link #setResponseVars(Map)}.
     *
     * @param constants the constants
     * @param env the environment values
     * @param vars the runtime-mutable variables
     * @param params the execution-time parameters
     */
    public RuntimeData(
            Map<String, Object> constants,
            Map<String, Object> env,
            Map<String, Object> vars,
            Map<String, Object> params) {
        data = new HashMap<>();
        data.put(CONSTANTS, constants);
        data.put(ENV, env);
        data.put(MISC, new HashMap<>());
        data.put(VARS, vars);
        data.put(RESPONSE_VARS, new HashMap<>());
        data.put(PARAMS, params);
    }

    /**
     * Returns one namespace by key.
     *
     * @param key the namespace, e.g. {@code constants}
     * @return the namespace's values, or {@code null} if there is no such namespace
     */
    public Map<String, Object> getData(String key) {
        return data.get(key);
    }

    /**
     * Returns the {@code constants} namespace.
     *
     * @return the constants
     */
    public Map<String, Object> getConstants() {
        return getData(CONSTANTS);
    }

    /**
     * Returns the {@code env} namespace — the per-environment values, nothing to do with OS
     * environment variables.
     *
     * @return the environment values
     */
    public Map<String, Object> getEnv() {
        return getData(ENV);
    }

    /**
     * Returns the {@code vars} namespace, which a suite writes to at run time.
     *
     * @return the runtime variables
     */
    public Map<String, Object> getVars() {
        return getData(VARS);
    }

    /**
     * Returns the {@code responseVars} namespace, holding the previous response.
     *
     * @return the response variables
     */
    public Map<String, Object> getResponseVars() {
        return getData(RESPONSE_VARS);
    }

    /**
     * Returns the {@code params} namespace — the execution-time parameters behind
     * {@code ${params.x}}, unrelated to a condition func's {@code args}.
     *
     * @return the execution parameters
     */
    public Map<String, Object> getParams() {
        return getData(PARAMS);
    }

    /**
     * Replaces the {@code responseVars} namespace with the response of the request that just ran.
     * <p>
     * The namespace is <strong>renewed per request, never merged</strong>: whatever the previous
     * request left is gone, which is what makes {@code ${response.*}} mean "the request immediately
     * before this one" rather than "the most recent request that happened to set this key".
     * <p>
     * <strong>An empty map is the meaningful case, not a degenerate one.</strong> A request that was
     * skipped or that never reached the server has no response, and passing empty is how that is
     * said: a later {@code ${response.…}} then fails rather than quietly answering from further back,
     * because an assertion that passes against some earlier request's response is worse than one that
     * stops and says why.
     * <p>
     * The map is copied, so a caller may keep and reuse the one it passed.
     *
     * @param responseVars the response flattened into namespace form, or {@code null}/empty where
     *        there was no response
     */
    public void setResponseVars(Map<String, Object> responseVars) {
        data.put(RESPONSE_VARS, responseVars == null ? new HashMap<>() : new HashMap<>(responseVars));
    }

    /**
     * Clears the {@code responseVars}.
     */
    public void clearResponseVars() {
        setResponseVars(null);
    }

    /**
     * Records a successful {@code setVars} capture, clearing any tombstone the key carried.
     * <p>
     * The pairing is the reason this exists rather than callers writing to {@link #getVars()}
     * directly: a capture that finally succeeds — on a retry, or on a later request setting the same
     * key — must leave an ordinary variable behind rather than a poisoned one, and a caller that
     * remembers the {@code put} and forgets the clear leaves a variable that reads as failed.
     *
     * @param name the variable to set
     * @param value the captured value
     * @throws dev.pbroman.brat.core.exception.BratException if {@code name} is {@code null}
     */
    public void captureVar(String name, Object value) {
        Require.nonNull(name, "The var name must not be null");
        getData(VARS).put(name, value);
        tombstones.remove(name);
    }

    /**
     * Records that a {@code setVars} capture failed, leaving a {@link CaptureTombstone} on the key.
     * <p>
     * <strong>Any existing value for {@code name} is removed.</strong> A stale value from an earlier
     * request would otherwise be found first and the tombstone never reached, which is the
     * misattribution this whole mechanism exists to prevent — the reader would get a plausible value
     * belonging to a different request rather than a message naming the failure.
     * <p>
     * The tombstone records {@link #getCurrentPath()} as it stands now — the request whose capture
     * failed, frozen here because whoever reads it later is standing at a different request.
     * <strong>It is only meaningful if the path is being kept current</strong>, which is the job of
     * whatever drives requests; a run that never sets it leaves tombstones with no location, which
     * the message reporting one renders as {@code unknown}.
     *
     * @param name the variable the capture would have set
     * @param message why the capture failed
     * @throws dev.pbroman.brat.core.exception.BratException if {@code name} is {@code null}
     */
    public void captureFailed(String name, String message) {
        Require.nonNull(name, "The var name must not be null");
        getData(VARS).remove(name);
        tombstones.put(name, new CaptureTombstone(message, currentPath));
    }

    /**
     * Returns the tombstone left by a failed capture of {@code name}, if there is one.
     *
     * @param name the variable to ask about, or {@code null}
     * @return the tombstone, or {@code null} if {@code name} was never captured, was captured
     *         successfully, or is {@code null}. Never throws — a caller asking about an arbitrary key
     *         needs no guard
     */
    public CaptureTombstone getTombstone(String name) {
        return tombstones.get(name);
    }
}
