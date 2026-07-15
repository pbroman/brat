package dev.pbroman.brat.core.data.runtime;

import static dev.pbroman.brat.core.util.Constants.CONSTANTS;
import static dev.pbroman.brat.core.util.Constants.ENV;
import static dev.pbroman.brat.core.util.Constants.MISC;
import static dev.pbroman.brat.core.util.Constants.PARAMS;
import static dev.pbroman.brat.core.util.Constants.RESPONSE_VARS;
import static dev.pbroman.brat.core.util.Constants.VARS;

import java.util.HashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

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
     * Equivalent to {@link #RuntimeData(Map, Map, Map, Map, Map)} with {@code vars},
     * {@code responseVars}, and {@code params} defaulted to empty, mutable maps.
     *
     * @param constants the constants
     * @param env the environment values
     */
    public RuntimeData(Map<String, Object> constants, Map<String, Object> env) {
        this(constants, env, new HashMap<>(), new HashMap<>(), new HashMap<>());
    }

    /**
     * Equivalent to {@link #RuntimeData(Map, Map, Map, Map, Map)} with {@code params}
     * defaulted to an empty, mutable map.
     *
     * @param constants the constants
     * @param env the environment values
     * @param vars the runtime-mutable variables
     * @param responseVars the response variables
     */
    public RuntimeData(Map<String, Object> constants,
                       Map<String, Object> env,
                       Map<String, Object> vars,
                       Map<String, Object> responseVars) {
        this(constants, env, vars, responseVars, new HashMap<>());
    }

    /**
     * Constructs the full set of namespaces.
     *
     * @param constants the constants
     * @param env the environment values
     * @param vars the runtime-mutable variables
     * @param responseVars the response variables
     * @param params the execution-time parameters
     */
    public RuntimeData(Map<String, Object> constants,
                       Map<String, Object> env,
                       Map<String, Object> vars,
                       Map<String, Object> responseVars,
                       Map<String, Object> params) {
        data = new HashMap<>();
        data.put(CONSTANTS, constants);
        data.put(ENV, env);
        data.put(MISC, new HashMap<>());
        data.put(VARS, vars);
        data.put(RESPONSE_VARS, responseVars);
        data.put(PARAMS, params);
    }

    public Map<String, Object> getData(String key) {
        return data.get(key);
    }

    public Map<String, Object> getConstants() {
        return getData(CONSTANTS);
    }

    public Map<String, Object> getEnv() {
        return getData(ENV);
    }

    public Map<String, Object> getVars() {
        return getData(VARS);
    }

    public Map<String, Object> getResponseVars() {
        return getData(RESPONSE_VARS);
    }

    public Map<String, Object> getParams() {
        return getData(PARAMS);
    }

}
