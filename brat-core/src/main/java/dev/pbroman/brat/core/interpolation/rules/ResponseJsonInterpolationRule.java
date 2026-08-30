package dev.pbroman.brat.core.interpolation.rules;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import com.jayway.jsonpath.JsonPath;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.api.interpolation.InterpolationRule;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.interpolation.InterpolationPatterns;

import static dev.pbroman.brat.core.interpolation.InterpolationChecks.requireNamespaces;
import static dev.pbroman.brat.core.util.Constants.JSON;
import static dev.pbroman.brat.core.util.Constants.RESPONSE_JSON;
import static dev.pbroman.brat.core.util.Constants.RESPONSE_VARS;
import static dev.pbroman.brat.core.util.Constants.VARIABLE_GROUP_NAME;
import static dev.pbroman.brat.core.util.Require.nonNull;

/**
 * An {@link InterpolationRule} resolving {@code ${response.json.<jsonPath>}} against the previous
 * response's JSON body.
 * <p>
 * Unlike the namespace rules it sits alongside, this implements {@link InterpolationRule} directly
 * rather than extending {@link AbstractInterpolationRule}: that base resolves to a {@code String},
 * and this rule's whole point is to hand on what JsonPath produced — a {@code List} for an array, a
 * {@code Map} for an object, an {@code Integer} for {@code ._length} — so a condition can compare
 * structures instead of their text forms.
 * <p>
 * The base's {@code :-} fallback chain does not apply here for the same reason it is not inherited:
 * a JSONPath may legitimately contain a {@code :-}, so there is no safe place to split one. A path
 * that resolves to nothing is a {@link BratException}, not a fallback.
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
            case Integer _ -> throw new BratException("Cannot get length of an integer");
            case Double _ -> throw new BratException("Cannot get length of a double value");
            case null, default -> throw new BratException("Cannot get length of an unknown object");
        });
    }

    /**
     * Resolves a {@code ${response.json.…}} token to the value at its JSONPath, keeping that value's
     * type.
     * <p>
     * Ownership is decided on the token alone, before {@code runtimeData} is looked at, so a token of
     * another namespace is declined rather than failing on a missing {@code responseVars}.
     *
     * @param input the token to resolve
     * @param runtimeData the object containing values; must hold the {@code responseVars} namespace
     * @return the outcome holding the resolved value, typed as JsonPath produced it; or
     *         {@link Optional#empty()} if {@code input} is not a whole {@code ${response.json.…}}
     *         token, leaving it for another rule
     * @throws BratException if {@code input} is {@code null}; or, for a token this rule claims, if
     *         {@code runtimeData} is {@code null}, if it has no {@code responseVars}, if the response
     *         holds no JSON, if the token names no path, if the path is malformed or matches nothing
     *         in the body, or if the trailing {@code ._…} names no known function
     */
    @Override
    public Optional<InterpolationOutcome> outcome(String input, RuntimeData runtimeData) {
        nonNull(input, "Cannot interpolate a null input");
        if (!InterpolationPatterns.isToken(input)) {
            return Optional.empty();
        }
        var matcher =
                InterpolationPatterns.groupingPatternForVariable(RESPONSE_JSON).matcher(input);
        if (!matcher.find()) {
            return Optional.empty();
        }
        requireNamespaces(runtimeData, RESPONSE_VARS);
        var jsonValue = runtimeData.getResponseVars().get(JSON);
        nonNull(jsonValue, "The json response must not be null");
        var json = jsonValue.toString();

        var pathExpr = matcher.group(VARIABLE_GROUP_NAME);
        nonNull(pathExpr, "The reference '" + input + "' names no JSONPath.");
        var pathExpression = pathExpr.split("\\._");
        var jsonPath = pathExpression[0];
        var result = read(json, jsonPath, input);

        if (pathExpression.length == 2) {
            var jsonFunction = pathExpression[1];
            var function = functionMap.get(jsonFunction);
            if (function == null) {
                throw new BratException(String.format(
                        "The json function '_%s' in '%s' is not defined. Known functions are %s.",
                        jsonFunction,
                        input,
                        functionMap.keySet().stream().sorted().toList()));
            }
            result = function.apply(result);
        }
        return Optional.of(new InterpolationOutcome(result, input + " → " + result));
    }

    /**
     * Reads {@code jsonPath} out of {@code json}, converting JsonPath's own exceptions — a missing
     * path, a malformed path, an unparseable body — into the single exception type core reports.
     *
     * @throws BratException if the path cannot be read, naming the token rather than the body, which
     *         may hold values that must not be logged
     */
    private static Object read(String json, String jsonPath, String input) {
        try {
            return JsonPath.read(json, jsonPath);
        } catch (Exception e) {
            throw new BratException(
                    String.format("Cannot resolve '%s' against the response json: %s", input, e.getMessage()), e);
        }
    }
}
