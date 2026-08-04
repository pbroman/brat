package dev.pbroman.brat.core.interpolation.rules;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.jayway.jsonpath.JsonPath;
import dev.pbroman.brat.core.api.interpolation.InterpolationRule;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.interpolation.InterpolationPatterns;
import org.apache.commons.lang3.StringUtils;

import static dev.pbroman.brat.core.interpolation.InterpolationChecks.requireNamespaces;
import static dev.pbroman.brat.core.util.Constants.JSON;
import static dev.pbroman.brat.core.util.Constants.RESPONSE_JSON_SHORTHAND;
import static dev.pbroman.brat.core.util.Constants.RESPONSE_VARS;
import static dev.pbroman.brat.core.util.Constants.VARIABLE_GROUP_NAME;

/**
 * An {@link InterpolationRule} for response json.
 */
public final class ResponseJsonInterpolationRule extends AbstractInterpolationRule {

    protected Map<String, Function<Object, Object>> functionMap;

    /**
     * Constructs an {@link InterpolationRule} for response json.
     *
     * @param patterns the {@link InterpolationPatterns}
     */
    public ResponseJsonInterpolationRule(InterpolationPatterns patterns) {
        super(RESPONSE_JSON_SHORTHAND, patterns);
        this.initFunctionMap();
    }

    @SuppressWarnings("rawtypes")
    private void initFunctionMap() {
        functionMap = new HashMap<>();
        functionMap.put("isArray", object -> object instanceof List);
        functionMap.put("isObject", object -> object instanceof Map);
        functionMap.put("isString", object -> object instanceof String);
        functionMap.put("isInteger", object -> object instanceof Integer);
        functionMap.put("isDouble", object -> object instanceof Double);
        functionMap.put("length", object -> switch (object) {
            case List list -> list.size();
            case Map map -> map.size();
            case String s -> s.length();
            case Integer ignored -> throw new IllegalArgumentException("Cannot get length of an integer");
            case Double ignored -> throw new IllegalArgumentException("Cannot get length of a double value");
            case null, default -> throw new IllegalArgumentException("Cannot get length of an unknown object");
        });
    }

    @Override
    public String resolve(String input, RuntimeData runtimeData) {
        if (StringUtils.isBlank(input)) {
            return input;
        }
        requireNamespaces(runtimeData, RESPONSE_VARS);

        var matcher =
                patterns.getGroupingPatternForVariable(RESPONSE_JSON_SHORTHAND).matcher(input);
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
