package dev.pbroman.brat.core.interpolation.rules;

import java.util.Map;
import java.util.stream.Stream;

import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.interpolation.AbstractInterpolationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static dev.pbroman.brat.core.util.Constants.JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ResponseJsonInterpolationRuleTest extends AbstractInterpolationTest {

    protected RuntimeData setUpRuntimeData() {
        Map<String, Object> responseVars = Map.of(JSON, jsonBody);
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
                Arguments.of("${rj.id}", "123"),
                Arguments.of("${rj.array[0].test}", "testVal"),
                // Functions keep the type JsonPath produced - a boolean is a Boolean, a length an
                // Integer - so a condition can compare values rather than their text forms.
                Arguments.of("${rj.array[0].test._isString}", true),
                Arguments.of("${rj.array[0]._isObject}", true),
                Arguments.of("${rj.array._isArray}", true),
                Arguments.of("${rj.id._length}", 3),
                Arguments.of("${rj.array._length}", 2),
                Arguments.of("${rj.array[0]._length}", 1),
                Arguments.of("${rj.object._length}", 0),
                Arguments.of("${rj.integer._isInteger}", true),
                Arguments.of("${rj.double._isDouble}", true));
    }

    private static Stream<Arguments> structuredPaths() {
        return Stream.of(
                Arguments.of("${rj.array}", java.util.List.class),
                Arguments.of("${rj.object}", Map.class),
                Arguments.of("${rj.array[0]}", Map.class),
                Arguments.of("${rj.integer}", Integer.class),
                Arguments.of("${rj.double}", Double.class));
    }

    @BeforeEach
    void setup() {
        underTest = new ResponseJsonInterpolationRule();
    }

    @ParameterizedTest
    @MethodSource("jsonPaths")
    void simpleJsonPathsTests(String input, Object expected) throws Exception {
        // when
        var result = underTest.interpolate(input, runtimeData);

        // then
        assertThat(result).isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource("structuredPaths")
    void interpolate_keepsTheTypeJsonPathProduced(String input, Class<?> expectedType) {
        // when
        var result = underTest.interpolate(input, runtimeData);

        // then - a structure arrives as a structure, not as its toString
        assertThat(result).isInstanceOf(expectedType);
    }

    @Test
    void outcome_leavesANonJsonTokenUnchanged() {
        // when
        var result = underTest.outcome("${vars.somethingElse}", runtimeData);

        // then
        assertThat(result.value()).isEqualTo("${vars.somethingElse}");
    }

    @Test
    void outcome_throwsWhenTheResponseHasNoJson() {
        // given
        var withoutJson = new RuntimeData(Map.of(), Map.of(), Map.of(), Map.of());

        // when / then
        assertThatThrownBy(() -> underTest.outcome("${rj.id}", withoutJson))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("json response");
    }
}
