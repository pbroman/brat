package dev.pbroman.brat.core.interpolation.rules;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.interpolation.AbstractInterpolationTest;
import dev.pbroman.brat.core.interpolation.InterpolationRuleDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static dev.pbroman.brat.core.util.Constants.BODY;
import static dev.pbroman.brat.core.util.Constants.HEADERS;
import static dev.pbroman.brat.core.util.Constants.JSON;
import static dev.pbroman.brat.core.util.Constants.STATUS_CODE;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class ResponseInterpolationIntegrationRuleTest extends AbstractInterpolationTest {

    @Override
    protected String ownToken() {
        return "${response.headers.moo}";
    }

    private static final String body = "myBody";
    private static final String statusCode = "200";
    private Interpolation dispatcher;

    /** The dispatcher is an {@link Interpolation}, not a rule, so it answers rather than declining. */
    @Override
    protected Object interpolate(String input, RuntimeData data) {
        return dispatcher.interpolate(input, data);
    }

    private static final String contentTypeHeader = "Content-Type";
    private static final String contentType = "application/json";

    @BeforeEach
    void setup() {
        dispatcher = new InterpolationRuleDispatcher(List.of(
                new ResponseBodyInterpolationRule(),
                new ResponseHeaderInterpolationRule(),
                new ResponseJsonInterpolationRule(),
                new ResponseStatusCodeInterpolationRule()));
    }

    protected RuntimeData setUpRuntimeData() {
        var responseVars = Map.of(
                BODY,
                body,
                STATUS_CODE,
                statusCode,
                HEADERS,
                Map.of(contentTypeHeader, contentType),
                JSON,
                "{\"name\":\"John\"}");
        return new RuntimeData(Map.of(), Map.of(), Map.of(), responseVars);
    }

    private static Stream<Arguments> responseTests() {
        return Stream.of(
                Arguments.of("${response.body}", body),
                Arguments.of("${response.statusCode}", statusCode),
                Arguments.of("${response.headers.Content-Type}", contentType),
                // The header rule is the one response namespace carrying a key, so it is the one the
                // ':-' fallback chain reaches.
                Arguments.of("${response.headers.Content-Type:-text/plain}", contentType),
                Arguments.of("${response.json.$.name}", "John"));
    }

    @ParameterizedTest
    @MethodSource("responseTests")
    void happyPaths(String input, String expected) throws Exception {
        // when
        var result = interpolate(input, runtimeData);

        // then
        assertThat(result).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "${response.bogus}", // no such response variable
                "${response.bogus.field}", // ... and none with a path either
                "${response.headersFoo.bar}", // a namespace is matched whole, never as a prefix
                "${responseXheaders.Content-Type}", // the '.' in a namespace is a '.', not "any character"
                "${response.bodyish}", // an exact-token namespace is exact
            })
    void interpolate_passesAnUnclaimedResponseTokenThrough(String input) {
        // when
        var result = interpolate(input, runtimeData);

        // then - no rule recognizes it, and an unclaimed token is literal text rather than an error
        assertThat(result).isEqualTo(input);
    }
}
