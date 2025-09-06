package dev.pbroman.brat.core.interpolation.rules;

import static dev.pbroman.brat.core.util.CheckUtils.checkInterpolationArgs;
import static dev.pbroman.brat.core.util.Constants.CONSTANTS;
import static dev.pbroman.brat.core.util.Constants.JSON;
import static dev.pbroman.brat.core.util.Constants.RESPONSE_JSON_SHORTHAND;
import static dev.pbroman.brat.core.util.Constants.RESPONSE_VARS;
import static dev.pbroman.brat.core.util.Constants.VARIABLE_GROUP_NAME;
import static java.util.Objects.requireNonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import com.jayway.jsonpath.JsonPath;
import dev.pbroman.brat.core.exception.ValidationException;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.tools.InterpolationTools;
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONArray;

@Slf4j
public class ResponseJsonInterpolationRule extends AbstractInterpolationRule {

    protected Map<String, Function<Object, Object>> functionMap = new HashMap<>();

    public ResponseJsonInterpolationRule(InterpolationTools tools) {
        super(RESPONSE_JSON_SHORTHAND, tools);
        this.initFunctionMap();
    }

    @SuppressWarnings("rawtypes")
    private void initFunctionMap() {
        functionMap.put("isArray", object -> object instanceof JSONArray);
        functionMap.put("isObject", object -> object instanceof Map);
        functionMap.put("isString", object -> object instanceof String);
        functionMap.put("isInteger", object -> object instanceof Integer);
        functionMap.put("isDouble", object -> object instanceof Double);
        functionMap.put("length", object -> switch (object) {
            case JSONArray array -> array.toArray().length;
            case Map map -> map.size();
            case String s -> s.length();
            case Integer ignored -> throw new IllegalArgumentException("Cannot get length of an integer");
            case Double ignored -> throw new IllegalArgumentException("Cannot get length of a double value");
            case null, default -> throw new IllegalArgumentException("Cannot get length of an unknown object");
        });
        extendFunctionMap();
    }

    /**
     * Override this method to extend the function map.
     */
    protected void extendFunctionMap() {

    }

    @Override
    public String interpolate(String input, RuntimeData runtimeData) throws ValidationException {
        checkInterpolationArgs(input, runtimeData, RESPONSE_VARS);

        var matcher = tools.getGroupingPatternForVariable(RESPONSE_JSON_SHORTHAND).matcher(input);
        if (!matcher.find()) {
            return input;
        }
        var json = runtimeData.getResponseVars().get(JSON).toString();
        if (json == null) {
            throw new IllegalArgumentException("The json response must not be null");
        }

        var pathExpr = matcher.group(VARIABLE_GROUP_NAME);
        var pathExpression = pathExpr.split("\\._");
        var jsonPath = pathExpression[0];
        var result = JsonPath.read(json, jsonPath);

        if (pathExpression.length == 2) {
            var jsonFunction = pathExpression[1];
            result = functionMap.get(jsonFunction).apply(result);
        }
        return result.toString();
    }

}
