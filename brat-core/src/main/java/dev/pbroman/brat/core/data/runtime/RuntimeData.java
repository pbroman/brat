package dev.pbroman.brat.core.data.runtime;

import static dev.pbroman.brat.core.util.Constants.CONSTANTS;
import static dev.pbroman.brat.core.util.Constants.ENV;
import static dev.pbroman.brat.core.util.Constants.MISC;
import static dev.pbroman.brat.core.util.Constants.RESPONSE_VARS;
import static dev.pbroman.brat.core.util.Constants.VARS;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.data.result.Validation;
import lombok.Getter;
import lombok.Setter;

@Getter
public class RuntimeData {

    private final Map<String, Map<String, Object>> data;
    //private final List<RequestResult> requestResults;
    private final List<Validation> validations;
    @Setter
    private String currentPath;
    @Setter
    private int currentRequestNo;


    public RuntimeData(Map<String, Object> constants, Map<String, Object> env) {
        data = new HashMap<>();
        data.put(CONSTANTS, constants);
        data.put(ENV, env);
        data.put(MISC, new HashMap<>());
        data.put(VARS, new HashMap<>());
        data.put(RESPONSE_VARS, new HashMap<>());
        validations = new ArrayList<>();
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
