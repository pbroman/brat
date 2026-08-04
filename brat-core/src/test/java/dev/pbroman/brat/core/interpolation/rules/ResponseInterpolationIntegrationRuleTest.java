package dev.pbroman.brat.core.interpolation.rules;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class ResponseInterpolationIntegrationRuleTest extends AbstractInterpolationTest {

    private static final String body = "myBody";
    private static final String statusCode = "200";
    private static final String contentTypeHeader = "Content-Type";
    private static final String contentType = "application/json";

    @BeforeEach
    void setup() {
        underTest = new InterpolationRuleDispatcher(List.of(
                new ResponseBodyInterpolationRule(patterns),
                new ResponseHeaderInterpolationRule(patterns),
                new ResponseJsonInterpolationRule(patterns),
                new ResponseShorthandInterpolationRule(patterns),
                new ResponseStatusCodeInterpolationRule(patterns)));
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
                Arguments.of("${rb}", body),
                Arguments.of("${response.statusCode}", statusCode),
                Arguments.of("${sc}", statusCode),
                Arguments.of("${response.headers.Content-Type}", contentType),
                Arguments.of("${rh.Content-Type}", contentType),
                Arguments.of("${response.json.$.name}", "John"),
                Arguments.of("${rj.$.name}", "John"));
    }

    @ParameterizedTest
    @MethodSource("responseTests")
    void happyPaths(String input, String expected) throws Exception {
        // when
        var result = underTest.interpolate(input, runtimeData);

        // then
        assertThat(result).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "${response.bogus}", // no such response variable
                "${response.bogus.field}", // ... and none with a path either
                "${response.headersFoo.bar}", // only a whole leading segment translates, not a prefix
            })
    void interpolate_throwsForAnUnknownResponseVariable(String input) {
        // when / then
        assertThatThrownBy(() -> underTest.interpolate(input, runtimeData))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("is not defined");
    }
}
