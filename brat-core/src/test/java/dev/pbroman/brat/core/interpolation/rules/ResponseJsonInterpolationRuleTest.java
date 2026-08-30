package dev.pbroman.brat.core.interpolation.rules;

import java.util.Map;
import java.util.stream.Stream;

import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
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

    @Override
    protected String ownToken() {
        return "${response.json.$.moo}";
    }

    protected RuntimeData setUpRuntimeData() {
        Map<String, Object> responseVars = Map.of(JSON, jsonBody);
        var runtimeData = new RuntimeData(Map.of(), Map.of());
        runtimeData.setResponseVars(responseVars);
        return runtimeData;
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
                Arguments.of("${response.json.id}", "123"),
                Arguments.of("${response.json.array[0].test}", "testVal"),
                // Functions keep the type JsonPath produced - a boolean is a Boolean, a length an
                // Integer - so a condition can compare values rather than their text forms.
                Arguments.of("${response.json.array[0].test._isString}", true),
                Arguments.of("${response.json.array[0]._isObject}", true),
                Arguments.of("${response.json.array._isArray}", true),
                Arguments.of("${response.json.id._length}", 3),
                Arguments.of("${response.json.array._length}", 2),
                Arguments.of("${response.json.array[0]._length}", 1),
                Arguments.of("${response.json.object._length}", 0),
                Arguments.of("${response.json.integer._isInteger}", true),
                Arguments.of("${response.json.double._isDouble}", true));
    }

    private static Stream<Arguments> structuredPaths() {
        return Stream.of(
                Arguments.of("${response.json.array}", java.util.List.class),
                Arguments.of("${response.json.object}", Map.class),
                Arguments.of("${response.json.array[0]}", Map.class),
                Arguments.of("${response.json.integer}", Integer.class),
                Arguments.of("${response.json.double}", Double.class));
    }

    @BeforeEach
    void setup() {
        underTest = new ResponseJsonInterpolationRule();
    }

    @ParameterizedTest
    @MethodSource("jsonPaths")
    void simpleJsonPathsTests(String input, Object expected) throws Exception {
        // when
        var result = interpolate(input, runtimeData);

        // then
        assertThat(result).isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource("structuredPaths")
    void interpolate_keepsTheTypeJsonPathProduced(String input, Class<?> expectedType) {
        // when
        var result = interpolate(input, runtimeData);

        // then - a structure arrives as a structure, not as its toString
        assertThat(result).isInstanceOf(expectedType);
    }

    @Test
    void outcome_leavesANonJsonTokenUnchanged() {
        // when
        var result = claimed("${vars.somethingElse}", runtimeData);

        // then
        assertThat(result.value()).isEqualTo("${vars.somethingElse}");
    }

    @Test
    void outcome_declinesTextThatMerelyHoldsAToken() {
        // when - a rule is handed one whole token, never the field around it
        var result = underTest.outcome("count: ${response.json.id}", runtimeData);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void outcome_declinesAJsonTokenWithoutLookingAtRuntimeData() {
        // when - ownership is decided on the token alone, so a foreign token never touches the data
        var result = underTest.outcome("${vars.somethingElse}", null);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void outcome_throwsABratExceptionForAPathThatMatchesNothing() {
        // when / then - JsonPath's own PathNotFoundException must not escape core
        assertThatThrownBy(() -> claimed("${response.json.$.missing}", runtimeData))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("${response.json.$.missing}");
    }

    @Test
    void outcome_throwsABratExceptionForAMalformedPath() {
        // when / then
        assertThatThrownBy(() -> claimed("${response.json.$.[}", runtimeData))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("Cannot resolve");
    }

    @Test
    void outcome_throwsForAnUnknownJsonFunction() {
        // when / then
        assertThatThrownBy(() -> claimed("${response.json.id._bogus}", runtimeData))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("is not defined");
    }

    @Test
    void outcome_throwsWhenTheTokenNamesNoPath() {
        // when / then
        assertThatThrownBy(() -> claimed("${response.json.}", runtimeData))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("names no JSONPath");
    }

    @Test
    void outcome_throwsForTheLengthOfAnInteger() {
        // when / then - a number has no length, and saying so beats returning a digit count
        assertThatThrownBy(() -> claimed("${response.json.integer._length}", runtimeData))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("integer");
    }

    @Test
    void outcome_throwsForTheLengthOfADouble() {
        // when / then
        assertThatThrownBy(() -> claimed("${response.json.double._length}", runtimeData))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("double");
    }

    @Test
    void outcome_throwsForTheLengthOfSomethingWithNone() {
        // given - a boolean is none of the types _length knows
        var withBoolean = new RuntimeData(Map.of(), Map.of());
        withBoolean.setResponseVars(Map.of(JSON, "{\"flag\": true}"));

        // when / then
        assertThatThrownBy(() -> claimed("${response.json.flag._length}", withBoolean))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("length");
    }

    @Test
    void outcome_throwsWhenTheResponseHasNoJson() {
        // given
        var withoutJson = new RuntimeData(Map.of(), Map.of());

        // when / then
        assertThatThrownBy(() -> claimed("${response.json.id}", withoutJson))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("json response");
    }
}
