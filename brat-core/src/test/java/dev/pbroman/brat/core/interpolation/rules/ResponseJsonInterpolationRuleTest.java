package dev.pbroman.brat.core.interpolation.rules;

import static dev.pbroman.brat.core.util.Constants.JSON;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import dev.pbroman.brat.core.interpolation.AbstractInterpolationTest;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.tools.InterpolationTools;

public class ResponseJsonInterpolationRuleTest extends AbstractInterpolationTest {

    protected RuntimeData setUpRuntimeData() {
        Map<String, Object> responseVars = Map.of(
                JSON, jsonBody
        );
        return new RuntimeData(Map.of(), Map.of(), Map.of(), responseVars);
    }

    private final String jsonBody = """
            {
                "id": "123",
                "array": [ { "test": "testVal" }, { "array2": [{}] } ],
                "object": {},
                "integer": 42,
                "double": 42.0
            }
            """.strip();

    private static Stream<Arguments> jsonPaths() {
        return Stream.of(
                // Paths TODO extend!
                Arguments.of("${rj.id}", "123"),
                Arguments.of("${rj.array[0].test}", "testVal"),
                // Functions
                Arguments.of("${rj.array[0].test._isString}", "true"),
                Arguments.of("${rj.array[0]._isObject}", "true"),
                Arguments.of("${rj.array._isArray}", "true"),
                Arguments.of("${rj.id._length}", "3"),
                Arguments.of("${rj.array._length}", "2"),
                Arguments.of("${rj.array[0]._length}", "1"),
                Arguments.of("${rj.object._length}", "0"),
                Arguments.of("${rj.integer._isInteger}", "true"),
                Arguments.of("${rj.double._isDouble}", "true"),
                Arguments.of("${rj.id._isEmpty}", "false")
        );
    }

    @BeforeEach
    void setup() {
        underTest = new ExtendedResponseJsonInterpolationRule(tools);
    }

    @ParameterizedTest
    @MethodSource("jsonPaths")
    void simpleJsonPathsTests(String input, String expected) throws Exception{
        // when
        var result = underTest.interpolate(input, runtimeData);

        // then
        assertThat(result).isEqualTo(expected);
    }

    static class ExtendedResponseJsonInterpolationRule extends ResponseJsonInterpolationRule {
        public ExtendedResponseJsonInterpolationRule(InterpolationTools tools) {
            super(tools);
        }

        @Override
        protected void extendFunctionMap() {
            functionMap.put("isEmpty", object -> object instanceof String && ((String) object).isEmpty());
        }
    }
}
