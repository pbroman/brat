package dev.pbroman.brat.core.interpolation.functions;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeneratorFunctionsTest {

    private static String call(String name, String... args) {
        return GeneratorFunctions.functions().stream()
                .filter(function -> function.name().equals(name))
                .findFirst()
                .orElseThrow()
                .apply(List.of(args));
    }

    // --- uuid ---

    @Test
    void uuid_returnsARandomVersion4Uuid() {
        // when
        var result = call("uuid");

        // then
        assertThat(UUID.fromString(result).version()).isEqualTo(4);
        assertThat(result).isLowerCase();
    }

    @Test
    void uuid_returnsAFreshValueOnEveryCall() {
        // when — the property that makes setVars necessary to reuse one
        var first = call("uuid");
        var second = call("uuid");

        // then
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void uuid_throwsForAnyArgument() {
        // when / then
        assertThatThrownBy(() -> call("uuid", "x")).isInstanceOf(BratException.class);
    }

    // --- randomInt / randomLong ---

    @Test
    void randomInt_staysWithinBothInclusiveBounds() {
        // when — a narrow range, sampled enough to hit both ends
        var results = IntStream.range(0, 200)
                .mapToObj(i -> call("randomInt", "1", "3"))
                .toList();

        // then
        assertThat(results).allMatch(r -> Long.parseLong(r) >= 1 && Long.parseLong(r) <= 3);
        assertThat(results).contains("1", "3");
    }

    @Test
    void randomInt_acceptsASingleValueRange() {
        // when — min equals max, so the bounds being inclusive is the whole answer
        var result = call("randomInt", "7", "7");

        // then
        assertThat(result).isEqualTo("7");
    }

    @Test
    void randomInt_acceptsNegativeBounds() {
        // when
        var result = Long.parseLong(call("randomInt", "-10", "-5"));

        // then
        assertThat(result).isBetween(-10L, -5L);
    }

    @Test
    void randomLong_staysWithinBothInclusiveBounds() {
        // when
        var result = Long.parseLong(call("randomLong", "1000000000000", "1000000000002"));

        // then
        assertThat(result).isBetween(1000000000000L, 1000000000002L);
    }

    @Test
    void randomInt_throwsWhenTheLowerBoundIsAboveTheUpper() {
        // when / then
        assertThatThrownBy(() -> call("randomInt", "5", "1")).isInstanceOf(BratException.class);
    }

    @Test
    void randomInt_throwsForANonNumericBound() {
        // when / then
        assertThatThrownBy(() -> call("randomInt", "one", "3")).isInstanceOf(BratException.class);
    }

    @Test
    void randomLong_throwsForAnUpperBoundItCannotIncludeExclusively() {
        // when / then — max + 1 would overflow, and the bound is documented as inclusive
        assertThatThrownBy(() -> call("randomLong", "0", String.valueOf(Long.MAX_VALUE)))
                .isInstanceOf(BratException.class);
    }

    @Test
    void randomInt_throwsForTheWrongArgumentCount() {
        // when / then
        assertThatThrownBy(() -> call("randomInt", "1")).isInstanceOf(BratException.class);
    }

    // --- randomFloat ---

    @Test
    void randomFloat_staysWithinTheBounds() {
        // when
        var result = Double.parseDouble(call("randomFloat", "0.0", "1.0"));

        // then
        assertThat(result).isBetween(0.0, 1.0);
    }

    @Test
    void randomFloat_acceptsASingleValueRange() {
        // when
        var result = Double.parseDouble(call("randomFloat", "2.5", "2.5"));

        // then
        assertThat(result).isEqualTo(2.5);
    }

    @Test
    void randomFloat_throwsWhenTheLowerBoundIsAboveTheUpper() {
        // when / then
        assertThatThrownBy(() -> call("randomFloat", "1.0", "0.0")).isInstanceOf(BratException.class);
    }

    // --- randomFrom ---

    @Test
    void randomFrom_returnsOneOfItsArguments() {
        // when
        var results = IntStream.range(0, 100)
                .mapToObj(i -> call("randomFrom", "RED", "GREEN", "BLUE"))
                .toList();

        // then
        assertThat(results).isSubsetOf("RED", "GREEN", "BLUE");
        assertThat(results).contains("RED", "GREEN", "BLUE");
    }

    @Test
    void randomFrom_acceptsASingleArgument() {
        // when
        var result = call("randomFrom", "ONLY");

        // then
        assertThat(result).isEqualTo("ONLY");
    }

    @Test
    void randomFrom_throwsWithNoArguments() {
        // when / then
        assertThatThrownBy(() -> call("randomFrom")).isInstanceOf(BratException.class);
    }

    // --- randomString ---

    @Test
    void randomString_returnsTheRequestedLengthFromTheDefaultAlphabet() {
        // when
        var result = call("randomString", "8");

        // then
        assertThat(result).hasSize(8).matches("[A-Za-z0-9]{8}");
    }

    @Test
    void randomString_drawsFromAGivenAlphabet() {
        // when
        var result = call("randomString", "6", "ABCDEF0123456789");

        // then
        assertThat(result).hasSize(6).matches("[A-F0-9]{6}");
    }

    @Test
    void randomString_returnsAnEmptyStringForZeroLength() {
        // when
        var result = call("randomString", "0");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void randomString_throwsForANegativeLength() {
        // when / then
        assertThatThrownBy(() -> call("randomString", "-1")).isInstanceOf(BratException.class);
    }

    @Test
    void randomString_throwsForAnEmptyAlphabet() {
        // when / then
        assertThatThrownBy(() -> call("randomString", "4", "")).isInstanceOf(BratException.class);
    }

    @Test
    void randomString_throwsForALengthBeyondTheIntRange() {
        // when / then — narrowing this silently would wrap it to 0 and yield an empty string
        assertThatThrownBy(() -> call("randomString", "4294967296")).isInstanceOf(BratException.class);
    }

    @Test
    void randomString_throwsForALengthAboveTheCap() {
        // when / then — a length this large is a typo, not a request
        assertThatThrownBy(() -> call("randomString", "1000001")).isInstanceOf(BratException.class);
    }

    @Test
    void randomFloat_throwsForABoundTooLargeToRepresent() {
        // when / then — doubleValue saturates to infinity rather than failing
        assertThatThrownBy(() -> call("randomFloat", "0", "1e400")).isInstanceOf(BratException.class);
    }
}
