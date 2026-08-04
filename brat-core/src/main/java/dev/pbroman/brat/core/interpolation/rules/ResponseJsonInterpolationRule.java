package dev.pbroman.brat.core.interpolation.rules;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.jayway.jsonpath.JsonPath;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.api.interpolation.InterpolationRule;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.interpolation.InterpolationPatterns;
import org.apache.commons.lang3.StringUtils;

import static dev.pbroman.brat.core.interpolation.InterpolationChecks.requireNamespaces;
import static dev.pbroman.brat.core.util.Constants.JSON;
import static dev.pbroman.brat.core.util.Constants.RESPONSE_JSON_SHORTHAND;
import static dev.pbroman.brat.core.util.Constants.RESPONSE_VARS;
import static dev.pbroman.brat.core.util.Constants.VARIABLE_GROUP_NAME;
import static dev.pbroman.brat.core.util.Require.nonNull;

/**
 * An {@link InterpolationRule} resolving {@code ${rj.<jsonPath>}} against the previous response's
 * JSON body.
 * <p>
 * Unlike the namespace rules it sits alongside, this implements {@link InterpolationRule} directly
 * rather than extending {@link AbstractInterpolationRule}: that base resolves to a {@code String},
 * and this rule's whole point is to hand on what JsonPath produced — a {@code List} for an array, a
 * {@code Map} for an object, an {@code Integer} for {@code ._length} — so a condition can compare
 * structures instead of their text forms.
 */
public final class ResponseJsonInterpolationRule implements InterpolationRule {

    protected Map<String, Function<Object, Object>> functionMap;

    /**
     * Constructs an {@link InterpolationRule} for response json.
     *
     */
    public ResponseJsonInterpolationRule() {
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

    /**
     * Resolves a {@code ${rj.…}} token to the value at its JSONPath, keeping that value's type.
     *
     * @param input the token to resolve
     * @param runtimeData the object containing values; must hold the {@code responseVars} namespace
     * @return the outcome holding the resolved value, typed as JsonPath produced it; or
     *         {@code input} unchanged if it is not a {@code ${rj.…}} token, leaving it for another
     *         rule
     * @throws BratException if {@code input} is {@code null}
     * @throws IllegalArgumentException if {@code runtimeData} is {@code null}, if it has no
     *         {@code responseVars}, or if the response holds no JSON
     */
    @Override
    public InterpolationOutcome outcome(String input, RuntimeData runtimeData) {
        nonNull(input, "Cannot interpolate a null input");
        if (StringUtils.isBlank(input)) {
            return new InterpolationOutcome(input, input);
        }
        requireNamespaces(runtimeData, RESPONSE_VARS);

        var matcher = InterpolationPatterns.groupingPatternForVariable(RESPONSE_JSON_SHORTHAND)
                .matcher(input);
        if (!matcher.find()) {
            return new InterpolationOutcome(input, input);
        }
        var jsonValue = runtimeData.getResponseVars().get(JSON);
        if (jsonValue == null) {
            throw new IllegalArgumentException("The json response must not be null");
        }
        var json = jsonValue.toString();

        var pathExpr = matcher.group(VARIABLE_GROUP_NAME);
        var pathExpression = pathExpr.split("\\._");
        var jsonPath = pathExpression[0];
        var result = JsonPath.read(json, jsonPath);

        if (pathExpression.length == 2) {
            var jsonFunction = pathExpression[1];
            result = functionMap.get(jsonFunction).apply(result);
        }
        return new InterpolationOutcome(result, input + " → " + result);
    }
}
