package dev.pbroman.brat.core.interpolation.functions;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import dev.pbroman.brat.core.api.interpolation.BratFunction;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.interpolation.FunctionRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StandardFunctionsTest {

    private static List<String> namesOf(List<BratFunction> functions) {
        return functions.stream().map(BratFunction::name).toList();
    }

    @Test
    void all_gathersEveryGroup() {
        // when
        var names = namesOf(StandardFunctions.all());

        // then
        assertThat(names)
                .contains("uuid", "randomInt", "randomString")
                .contains("now", "epoch", "date")
                .contains("upper", "lower", "trim", "substring", "length", "replace", "default")
                .contains("base64", "base64Decode", "urlEncode", "urlDecode", "md5", "sha256", "hmacSha256")
                .contains("add", "subtract", "multiply", "divide", "mod");
    }

    @Test
    void all_holdsEveryFunctionOfEveryGroupAndNothingElse() {
        // given
        var expected = Stream.of(
                        GeneratorFunctions.functions(),
                        DateTimeFunctions.functions(),
                        TextFunctions.functions(),
                        EncodingFunctions.functions(),
                        MathFunctions.functions())
                .flatMap(List::stream)
                .map(BratFunction::name)
                .toList();

        // when
        var names = namesOf(StandardFunctions.all());

        // then — no group forgotten in the aggregator, and no name defined in two groups
        assertThat(names).containsExactlyInAnyOrderElementsOf(expected);
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

    @Test
    void gather_throwsIfTwoGroupsShareAName() {
        // given — the failure a plain flatten would turn into a silent override of one core
        // function by another
        var groups = List.of(
                List.of(BratFunction.of("upper", args -> "one")), List.of(BratFunction.of("upper", args -> "two")));

        // when / then
        assertThatThrownBy(() -> StandardFunctions.gather(groups))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("upper");
    }

    @Test
    void gather_comparesNamesIgnoringCase() {
        // given — a registry looks names up case-insensitively, so these two collide there too
        var groups = List.of(
                List.of(BratFunction.of("upper", args -> "one")), List.of(BratFunction.of("UPPER", args -> "two")));

        // when / then
        assertThatThrownBy(() -> StandardFunctions.gather(groups)).isInstanceOf(BratException.class);
    }

    @Test
    void gather_throwsIfOneGroupRepeatsAName() {
        // given — shadowing within a group is as silent as shadowing across two, so the check must
        // not be per-group; this pins that the seen-names set spans the whole flatten
        var groups = List.of(List.of(BratFunction.of("upper", args -> "one"), BratFunction.of("upper", args -> "two")));

        // when / then
        assertThatThrownBy(() -> StandardFunctions.gather(groups))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("upper");
    }

    @Test
    void gather_throwsForANullFunction() {
        // given
        var withNullFunction = new ArrayList<BratFunction>();
        withNullFunction.add(null);

        // when / then — a BratException, not the NullPointerException dereferencing it would give
        assertThatThrownBy(() -> StandardFunctions.gather(List.of(withNullFunction)))
                .isInstanceOf(BratException.class);
    }

    @Test
    void gather_throwsForANullName() {
        // given — of() does not validate the name, so a nameless function can reach here
        var groups = List.of(List.of(BratFunction.of(null, args -> "")));

        // when / then
        assertThatThrownBy(() -> StandardFunctions.gather(groups)).isInstanceOf(BratException.class);
    }

    @Test
    void gather_leavesABlankNameToTheRegistry() {
        // when — the registry is the single validator of names, so this is not gather's to reject
        var gathered = StandardFunctions.gather(List.of(List.of(BratFunction.of("  ", args -> ""))));

        // then
        assertThat(gathered).hasSize(1);
    }

    @Test
    void gather_keepsGroupOrder() {
        // given
        var groups = List.of(
                List.of(BratFunction.of("a", args -> "")),
                List.of(BratFunction.of("b", args -> ""), BratFunction.of("c", args -> "")));

        // when
        var gathered = StandardFunctions.gather(groups);

        // then
        assertThat(namesOf(gathered)).containsExactly("a", "b", "c");
    }

    @Test
    void all_isOrderedSoContributedFunctionsCanOverrideIt() {
        // given — the documented consumer pattern: standard first, contributed after
        var override = BratFunction.of("uuid", args -> "overridden");
        var functions = Stream.concat(StandardFunctions.all().stream(), Stream.of(override))
                .toList();

        // when
        var registry = new FunctionRegistry(functions);

        // then
        assertThat(registry.get("uuid").apply(List.of())).isEqualTo("overridden");
    }
}
