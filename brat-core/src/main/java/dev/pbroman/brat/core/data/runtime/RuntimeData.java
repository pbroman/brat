package dev.pbroman.brat.core.data.runtime;

import static dev.pbroman.brat.core.util.Constants.CONSTANTS;
import static dev.pbroman.brat.core.util.Constants.ENV;
import static dev.pbroman.brat.core.util.Constants.RESPONSE_VARS;
import static dev.pbroman.brat.core.util.Constants.VARS;

import java.util.HashMap;
import java.util.Map;

public class RuntimeData {

    private Map<String, Map<String, Object>> data;

    public RuntimeData(Map<String, Object> constants, Map<String, Object> env) {
        data = new HashMap<>();
        data.put(CONSTANTS, constants);
        data.put(ENV, env);
    }

    public RuntimeData(Map<String, Object> constants,
                       Map<String, Object> env,
                       Map<String, Object> vars,
                       Map<String, Object> responseVars) {
        this(constants, env);
        data.put(VARS, vars);
        data.put(RESPONSE_VARS, responseVars);
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


}
