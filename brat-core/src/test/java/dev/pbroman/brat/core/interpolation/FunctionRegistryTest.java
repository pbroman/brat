package dev.pbroman.brat.core.interpolation;

import java.util.ArrayList;
import java.util.List;

import dev.pbroman.brat.core.api.interpolation.BratFunction;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FunctionRegistryTest {

    private static final BratFunction UPPER =
            BratFunction.of("upper", args -> String.valueOf(args.getFirst()).toUpperCase());

    private static BratFunction named(String name) {
        return BratFunction.of(name, args -> name);
    }

    // --- get / has ---

    @Test
    void get_returnsTheRegisteredFunction() {
        // given
        var registry = new FunctionRegistry(List.of(UPPER));

        // when
        var result = registry.get("upper");

        // then
        assertThat(result).isSameAs(UPPER);
    }

    @Test
    void get_matchesTheNameCaseInsensitively() {
        // given
        var registry = new FunctionRegistry(List.of(UPPER));

        // when / then
        assertThat(registry.get("UPPER")).isSameAs(UPPER);
        assertThat(registry.get("Upper")).isSameAs(UPPER);
    }

    @Test
    void get_throwsForAnUnknownFunction() {
        // given
        var registry = new FunctionRegistry(List.of(UPPER));

        // when / then — a typo, not literal text
        assertThatThrownBy(() -> registry.get("uper"))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("uper");
    }

    @Test
    void get_throwsForANullOrBlankName() {
        // given
        var registry = new FunctionRegistry(List.of(UPPER));

        // when / then
        assertThatThrownBy(() -> registry.get(null)).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> registry.get("  ")).isInstanceOf(BratException.class);
    }

    @Test
    void has_reportsWhetherAFunctionIsRegistered() {
        // given
        var registry = new FunctionRegistry(List.of(UPPER));

        // when / then
        assertThat(registry.has("upper")).isTrue();
        assertThat(registry.has("UPPER")).isTrue();
        assertThat(registry.has("uper")).isFalse();
    }

    // --- constructor ---

    @Test
    void constructor_acceptsAnEmptyRegistry() {
        // when
        var registry = new FunctionRegistry(List.of());

        // then
        assertThat(registry.has("upper")).isFalse();
    }

    @Test
    void constructor_throwsForNullFunctions() {
        // when / then
        assertThatThrownBy(() -> new FunctionRegistry(null)).isInstanceOf(BratException.class);
    }

    @Test
    void constructor_throwsForANullElement() {
        // given
        var withNullElement = new ArrayList<BratFunction>();
        withNullElement.add(null);

        // when / then
        assertThatThrownBy(() -> new FunctionRegistry(withNullElement)).isInstanceOf(BratException.class);
    }

    @Test
    void constructor_throwsForANullOrBlankName() {
        // given — of() validates nothing, so an unusable name reaches the registry
        var nullName = named(null);
        var blankName = named("  ");

        // when / then
        assertThatThrownBy(() -> new FunctionRegistry(List.of(nullName))).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> new FunctionRegistry(List.of(blankName))).isInstanceOf(BratException.class);
    }

    @Test
    void constructor_throwsForANamePrefixedWithTheFunctionMarker() {
        // when / then — `__` is the syntax, not part of the name; registering it would make the
        // function reachable only as `${____upper}`
        assertThatThrownBy(() -> new FunctionRegistry(List.of(named("__upper"))))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("__");
    }

    @Test
    void constructor_throwsForTwoNamesDifferingOnlyInCase() {
        // given — lookup is case-insensitive, so these two could never both be reachable, and the
        // differing spelling says the author did not know the other existed
        var clashing = List.of(named("upper"), named("Upper"));

        // when / then
        assertThatThrownBy(() -> new FunctionRegistry(clashing))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("upper");
    }

    @Test
    void constructor_letsTheLaterFunctionWinARepeatedName() {
        // given — an exact repeat is a deliberate override, which is how a plugin replaces a
        // standard function
        var standard = BratFunction.of("upper", args -> "standard");
        var override = BratFunction.of("upper", args -> "override");

        // when
        var registry = new FunctionRegistry(List.of(standard, override));

        // then
        assertThat(registry.get("upper")).isSameAs(override);
    }

    @Test
    void constructor_doesNotRetainTheGivenCollection() {
        // given
        var functions = new ArrayList<BratFunction>();
        functions.add(UPPER);
        var registry = new FunctionRegistry(functions);

        // when
        functions.add(named("added-after-construction"));

        // then
        assertThat(registry.has("added-after-construction")).isFalse();
    }

    @Test
    void constructor_acceptsTwoFunctionsOfDifferentNames() {
        // when / then — only a repeated name is special; ordinary registration is unaffected
        assertThatCode(() -> new FunctionRegistry(List.of(named("upper"), named("lower"))))
                .doesNotThrowAnyException();
    }
}
