package dev.pbroman.brat.core.interpolation.rules;

import static dev.pbroman.brat.core.util.Constants.BODY;
import static dev.pbroman.brat.core.util.Constants.HEADERS;
import static dev.pbroman.brat.core.util.Constants.STATUS_CODE;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import dev.pbroman.brat.core.interpolation.AbstractInterpolationTest;
import dev.pbroman.brat.core.interpolation.InterpolationRuleDispatcher;
import dev.pbroman.brat.core.data.runtime.RuntimeData;

public class ResponseInterpolationIntegrationRuleTest extends AbstractInterpolationTest {

    private static final String body = "myBody";
    private static final String statusCode = "200";
    private static final String contentTypeHeader = "Content-Type";
    private static final String contentType = "application/json";

    @BeforeEach
    void setup() {
        underTest = new InterpolationRuleDispatcher(
                List.of(new ResponseBodyInterpolationRule(tools),
                        new ResponseHeaderInterpolationRule(tools),
                        new ResponseShorthandInterpolationRule(tools),
                        new ResponseStatusCodeInterpolationRule(tools))
        );
    }

    protected RuntimeData setUpRuntimeData() {
        var responseVars = Map.of(
                BODY, body,
                STATUS_CODE, statusCode,
                HEADERS, Map.of(contentTypeHeader, contentType)
        );
        return new RuntimeData(Map.of(), Map.of(), Map.of(), responseVars);
    }

    private static Stream<Arguments> responseTests() {
        return Stream.of(
                Arguments.of("${response.body}", body),
                Arguments.of("${rb}", body),
                Arguments.of("${response.statusCode}", statusCode),
                Arguments.of("${sc}", statusCode),
                Arguments.of("${response.headers.Content-Type}", contentType),
                Arguments.of("${rh.Content-Type}", contentType)
        );
    }

    @ParameterizedTest
    @MethodSource("responseTests")
    void happyPaths(String input, String expected) throws Exception {
        // when
        var result = underTest.interpolate(input, runtimeData);

        // then
        assertThat(result).isEqualTo(expected);
    }

}
