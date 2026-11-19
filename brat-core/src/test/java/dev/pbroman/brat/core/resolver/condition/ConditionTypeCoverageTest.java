package dev.pbroman.brat.core.resolver.condition;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.resolver.condition.rules.BooleanConditionResolverRule;
import dev.pbroman.brat.core.resolver.condition.rules.DateConditionResolverRule;
import dev.pbroman.brat.core.resolver.condition.rules.FormatConditionResolverRule;
import dev.pbroman.brat.core.resolver.condition.rules.JsonConditionResolverRule;
import dev.pbroman.brat.core.resolver.condition.rules.NumberConditionResolverRule;
import dev.pbroman.brat.core.resolver.condition.rules.StringConditionResolverRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Funcs on operand types they once answered wrongly or not at all, through every core rule at once, so
 * each case has one pinned answer whichever rule ends up giving it.
 */
class ConditionTypeCoverageTest {

    private final ConditionResolverRuleDispatcher dispatcher = new ConditionResolverRuleDispatcher(List.of(
            new StringConditionResolverRule(),
            new NumberConditionResolverRule(),
            new BooleanConditionResolverRule(),
            new DateConditionResolverRule(),
            new FormatConditionResolverRule(),
            new JsonConditionResolverRule()));

    static Stream<Arguments> answered() {
        return Stream.of(
                // a null a is only for isNull
                Arguments.of("isNull", null, null, true),
                Arguments.of("isNotNull", null, null, false),
                // the isNot spelling of a func whose name starts with Is
                Arguments.of("isNotIsoDateTime", "2026-11-07", null, true),
                // booleans
                Arguments.of("isTrue", "TRUE", null, true),
                Arguments.of("isFalse", "false", null, true),
                // size on text
                Arguments.of("hasSize", "abc", "3", true),
                // timestamps
                Arguments.of("isBefore", "2026-11-07T10:00:00Z", "2026-11-08T10:00:00Z", true),
                Arguments.of("isPast", "2026-11-07T10:00:00Z", null, true),
                Arguments.of("isEqualTo", "2026-11-07T10:00:00Z", "2026-11-07T11:00:00+01:00", true),
                Arguments.of("equals", "2026-11-07T10:00:00Z", "2026-11-07T11:00:00+01:00", false));
    }

    @ParameterizedTest(name = "{0} a={1} b={2} -> {3}")
    @MethodSource("answered")
    void resolve_answers(String func, Object a, Object b, boolean expected) {
        // when
        var result = dispatcher.resolve(new Condition(func, a, b));

        // then
        assertThat(result).isEqualTo(expected);
    }

    static Stream<Arguments> refused() {
        return Stream.of(
                // a null a, for any func but isNull
                Arguments.of("isNotEqualTo", null, "x"),
                Arguments.of("isNotOneOf", null, List.of("a", "b")),
                Arguments.of("notContains", null, "x"),
                Arguments.of("isEqualTo", null, "x"),
                Arguments.of("isEmpty", null, null),
                Arguments.of("isNotEmpty", null, null),
                Arguments.of("isNotBlank", null, null),
                Arguments.of("isTrue", null, null),
                Arguments.of("isNotTrue", null, null),
                Arguments.of("isFalse", List.of(true), null),
                Arguments.of("isFalse", Map.of("a", 1), null),
                Arguments.of("isFalse", "banana", null),
                Arguments.of("isFalse", "", null),
                Arguments.of("isTrue", "yes", null),
                Arguments.of("isUuid", List.of("x"), null),
                Arguments.of("isNotUrl", Map.of(), null),
                Arguments.of("isBefore", "2026-11-07", "2026-11-07T10:00:00Z"));
    }

    @ParameterizedTest(name = "{0} a={1} b={2} -> error")
    @MethodSource("refused")
    void resolve_refuses(String func, Object a, Object b) {
        // when / then
        assertThatThrownBy(() -> dispatcher.resolve(new Condition(func, a, b))).isInstanceOf(BratException.class);
    }

    @Test
    void resolve_refusesATypoOnANullAAsUnresolvable() {
        // when / then — no rule knows the name, so it is not even reported as a null
        assertThatThrownBy(() -> dispatcher.resolve(new Condition("isNotEqulTo", null, "x")))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("could not be resolved");
    }
}
