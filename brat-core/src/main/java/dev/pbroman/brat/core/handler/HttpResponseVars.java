package dev.pbroman.brat.core.handler;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import dev.pbroman.brat.core.data.result.HttpResponse;
import dev.pbroman.brat.core.util.Require;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static dev.pbroman.brat.core.util.Constants.BODY;
import static dev.pbroman.brat.core.util.Constants.HEADERS;
import static dev.pbroman.brat.core.util.Constants.JSON;
import static dev.pbroman.brat.core.util.Constants.STATUS_CODE;

/**
 * Builds the {@code responseVars} namespace from an HTTP response, so that {@code ${sc}},
 * {@code ${rb}}, {@code ${rh.…}} and {@code ${rj.…}} have something to resolve against.
 * <p>
 * This lives beside the HTTP handler rather than on {@code RuntimeData} deliberately. The namespace
 * is protocol-shaped — {@code statusCode} and {@code headers} mean nothing to an FTP request — so a
 * second protocol adds its own builder here instead of a second method on the type every protocol
 * shares.
 * <p>
 * <strong>The namespace is a lossy, interpolation-shaped view, not the response.</strong> Headers
 * collapse to one value each because {@code ${rh.Content-Type}} substitutes a single string. Nothing
 * is lost overall: the full multi-valued {@link HttpResponse} is what lands on the request's result,
 * and is where anything needing every value should look.
 */
public final class HttpResponseVars {

    private HttpResponseVars() {
        // utility class
    }

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    /**
     * Flattens a response into the {@code responseVars} namespace.
     * <p>
     * Four keys, each omitted rather than held as {@code null} when it has no value — a consumer
     * reads an absent key and a null value identically, and omitting keeps the map printable:
     * <ul>
     *   <li>{@code statusCode} — the status as an {@link Integer}. Always present</li>
     *   <li>{@code body} — the response body verbatim. Omitted when the response has no body</li>
     *   <li>{@code headers} — a {@code Map<String, String>} holding each header's <em>first</em>
     *       value, looked up case-insensitively. Always present, possibly empty</li>
     *   <li>{@code json} — the body verbatim again, present only when the body is a JSON
     *       <em>object or array</em>. A bare scalar does not qualify, however valid it is as a JSON
     *       document: a plain-text body of {@code 42} parses, and putting it here would only move its
     *       failure inside JSONPath. It is the body <em>string</em> rather than a parsed tree because
     *       every consumer feeds {@code toString()} to JSONPath; parsing here would round-trip the
     *       text for nothing</li>
     * </ul>
     * The {@code json} key's absence is how a rule tells "the response held no JSON" from "the
     * response held JSON with nothing at that path", which are different failures to an author.
     *
     * @param response the response to flatten
     * @return an unmodifiable map of the namespace, whose {@code headers} entry is itself
     *         unmodifiable and case-insensitive; never {@code null}
     * @throws dev.pbroman.brat.core.exception.BratException if {@code response} is {@code null}
     */
    public static Map<String, Object> of(HttpResponse response) {
        Require.nonNull(response, "The response must not be null");

        var map = new HashMap<String, Object>();
        map.put(STATUS_CODE, response.statusCode());
        map.put(HEADERS, flattenHeaders(response));
        if (response.body() != null) {
            map.put(BODY, response.body());
            addJson(response, map);
        }

        return Map.copyOf(map);
    }

    /**
     * Flattens each header to its first value, keeping the case the server sent.
     * <p>
     * The map is case-insensitive rather than the keys being lower-cased, and that is the only shape
     * that works: {@code AbstractInterpolationRule.simpleInterpolation} looks a header up with the
     * author's placeholder text verbatim, and it is shared with {@code vars} and {@code constants},
     * where case-sensitivity is correct. So the insensitivity has to live in the map.
     *
     * @param response the response whose headers to flatten
     * @return an unmodifiable, case-insensitive map of each header's first value
     */
    private static Map<String, String> flattenHeaders(HttpResponse response) {
        var flattened = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, List<String>> header : response.headers().entrySet()) {
            if (!header.getValue().isEmpty()) {
                flattened.put(header.getKey(), header.getValue().getFirst());
            }
        }
        return Collections.unmodifiableMap(flattened);
    }

    /**
     * Records the body under {@code json} when it is a JSON object or array, and otherwise leaves the
     * key absent.
     * <p>
     * <strong>An object or an array, not merely something Jackson parsed.</strong> {@code readTree}
     * accepts a bare scalar — {@code 42} is an {@code IntNode}, {@code true} a {@code BooleanNode} —
     * and answers an empty or blank input with a {@code MissingNode} rather than throwing. Either
     * would put a plain-text body under {@code json}, where {@code ${rj.…}} then fails inside JSONPath
     * instead of through the "no JSON here" path an author can act on.
     * <p>
     * Nothing is rethrown: an unparseable body is an ordinary response, not a failure of this method.
     *
     * @param response the response whose body to test
     * @param map the namespace being built, added to only when the body qualifies
     */
    private static void addJson(HttpResponse response, HashMap<String, Object> map) {
        try {
            var node = MAPPER.readTree(response.body());
            if (node.isObject() || node.isArray()) {
                map.put(JSON, response.body());
            }
        } catch (JacksonException e) {
            // Not JSON at all. Absence is the signal, so there is nothing to record and nothing to
            // rethrow - the same outcome as a scalar body, reached by a different route.
        }
    }
}
