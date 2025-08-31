package dev.pbroman.brat.core.data.runtime;

import java.util.HashMap;
import java.util.Map;

import lombok.Data;

@Data
public class RuntimeData {

    private Map<String, String> constants;
    private Map<String, String> env;
    private Map<String, Object> vars = new HashMap<>();
    private Map<String, Object> responseVars = new HashMap<>();

    public RuntimeData(Map<String, String> constants, Map<String, String> env) {
        this.constants = constants;
        this.env = env;
    }

    public RuntimeData(Map<String, String> constants,
                       Map<String, String> env,
                       Map<String, Object> vars,
                       Map<String, Object> responseVars) {
        this(constants, env);
        this.vars = vars;
        this.responseVars = responseVars;
    }

}
