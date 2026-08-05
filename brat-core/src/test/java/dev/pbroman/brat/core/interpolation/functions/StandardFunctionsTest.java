package dev.pbroman.brat.core.interpolation.functions;

import java.util.Map;

import dev.pbroman.brat.core.interpolation.FunctionRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StandardFunctionsTest {

    @Test
    void all_gathersEveryGroup() {
        // when
        var all = StandardFunctions.all();

        // then
        assertThat(all)
                .containsKeys("uuid", "randomInt", "randomString")
                .containsKeys("now", "epoch", "date")
                .containsKeys("upper", "lower", "trim", "substring", "length", "replace", "default")
                .containsKeys("base64", "base64Decode", "urlEncode", "urlDecode", "md5", "sha256", "hmacSha256")
                .containsKeys("add", "subtract", "multiply", "divide", "mod");
    }

    @Test
    void all_holdsEveryFunctionOfEveryGroupAndNothingElse() {
        // given
        var groups = Map.of(
                "generators", GeneratorFunctions.functions(),
                "date/time", DateTimeFunctions.functions(),
                "text", TextFunctions.functions(),
                "encoding", EncodingFunctions.functions(),
                "math", MathFunctions.functions());
        var expected = groups.values().stream()
                .flatMap(group -> group.keySet().stream())
                .toList();

        // when
        var all = StandardFunctions.all();

        // then — no group forgotten in the aggregator, and no name defined in two groups
        assertThat(all).containsOnlyKeys(expected);
        assertThat(expected).doesNotHaveDuplicates();
    }

    @Test
    void all_isAcceptedByTheRegistry() {
        // when — bare names, no __ prefix, no case collisions
        var registry = new FunctionRegistry(StandardFunctions.all());

        // then
        assertThat(registry.has("uuid")).isTrue();
        assertThat(registry.has("HMACSHA256")).isTrue();
    }
}
