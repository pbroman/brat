package dev.pbroman.brat.core.interpolation;

import java.util.HashMap;
import java.util.Map;

import dev.pbroman.brat.core.api.interpolation.BratFunction;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FunctionRegistryTest {

    private static final BratFunction UPPER =
            args -> String.valueOf(args.getFirst()).toUpperCase();

    @Test
    void get_returnsTheRegisteredFunction() {
        // given
        var registry = new FunctionRegistry(Map.of("upper", UPPER));

        // when
        var result = registry.get("upper");

        // then
        assertThat(result).isSameAs(UPPER);
    }

    @Test
    void get_matchesTheNameCaseInsensitively() {
        // given
        var registry = new FunctionRegistry(Map.of("upper", UPPER));

        // when / then
        assertThat(registry.get("UPPER")).isSameAs(UPPER);
        assertThat(registry.get("Upper")).isSameAs(UPPER);
    }

    @Test
    void get_throwsForAnUnknownFunction() {
        // given
        var registry = new FunctionRegistry(Map.of("upper", UPPER));

        // when / then — a typo, not literal text
        assertThatThrownBy(() -> registry.get("uper"))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("uper");
    }

    @Test
    void get_throwsForANullOrBlankName() {
        // given
        var registry = new FunctionRegistry(Map.of("upper", UPPER));

        // when / then
        assertThatThrownBy(() -> registry.get(null)).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> registry.get("  ")).isInstanceOf(BratException.class);
    }

    @Test
    void has_reportsWhetherAFunctionIsRegistered() {
        // given
        var registry = new FunctionRegistry(Map.of("upper", UPPER));

        // when / then
        assertThat(registry.has("upper")).isTrue();
        assertThat(registry.has("UPPER")).isTrue();
        assertThat(registry.has("uper")).isFalse();
    }

    @Test
    void constructor_acceptsAnEmptyRegistry() {
        // when
        var registry = new FunctionRegistry(Map.of());

        // then
        assertThat(registry.has("upper")).isFalse();
    }

    @Test
    void constructor_throwsForNullFunctions() {
        // when / then
        assertThatThrownBy(() -> new FunctionRegistry(null)).isInstanceOf(BratException.class);
    }

    @Test
    void constructor_throwsForANullOrBlankName() {
        // given
        var withNullName = new HashMap<String, BratFunction>();
        withNullName.put(null, UPPER);

        // when / then
        assertThatThrownBy(() -> new FunctionRegistry(withNullName)).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> new FunctionRegistry(Map.of("  ", UPPER))).isInstanceOf(BratException.class);
    }

    @Test
    void constructor_throwsForANullFunction() {
        // given
        var withNullFunction = new HashMap<String, BratFunction>();
        withNullFunction.put("upper", null);

        // when / then
        assertThatThrownBy(() -> new FunctionRegistry(withNullFunction)).isInstanceOf(BratException.class);
    }

    @Test
    void constructor_throwsForANamePrefixedWithTheFunctionMarker() {
        // when / then — `__` is the syntax, not part of the name; registering it would make the
        // function reachable only as `${____upper}`
        assertThatThrownBy(() -> new FunctionRegistry(Map.of("__upper", UPPER)))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("__");
    }

    @Test
    void constructor_throwsForTwoNamesDifferingOnlyInCase() {
        // given — lookup is case-insensitive, so these two could never both be reachable
        var clashing = Map.of("upper", UPPER, "Upper", UPPER);

        // when / then
        assertThatThrownBy(() -> new FunctionRegistry(clashing))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("upper");
    }
}
