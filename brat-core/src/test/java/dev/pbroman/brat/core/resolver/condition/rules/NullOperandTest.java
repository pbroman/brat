package dev.pbroman.brat.core.resolver.condition.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.api.resolver.ConditionPredicate;
import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The base rule's handling of a null {@code a}: only {@code isNull} answers, every other known func
 * throws, before {@code accepts} or a predicate is reached.
 */
class NullOperandTest {

    /** A rule that knows every func the null answer distinguishes, and records whether it was asked. */
    private static final class RecordingRule extends AbstractConditionResolverRule {

        private final List<String> calls = new ArrayList<>();

        RecordingRule() {
            super(Map.of(
                    "isNull", record("predicate"),
                    "isEmpty", record("predicate"),
                    "isBlank", record("predicate"),
                    "isWithin", record("predicate")));
        }

        private static ConditionPredicate record(String what) {
            return (a, b, args) -> {
                throw new AssertionError("the " + what + " must not see a null a");
            };
        }

        @Override
        protected String category() {
            return "Test";
        }

        @Override
        protected boolean accepts(Condition condition, String function) {
            calls.add("accepts");
            return true;
        }
    }

    private final RecordingRule rule = new RecordingRule();

    @ParameterizedTest
    @ValueSource(strings = {"isNull", "null", "ISNULL"})
    void resolve_isNullHoldsForANullA(String func) {
        // when
        var result = rule.resolve(new Condition(func, null, null));

        // then
        assertThat(result).contains(true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"isNotNull", "notNull", "!isNull"})
    void resolve_isNotNullFailsForANullA(String func) {
        // when
        var result = rule.resolve(new Condition(func, null, null));

        // then
        assertThat(result).contains(false);
    }

    @ParameterizedTest
    @ValueSource(strings = {"isEmpty", "isNotEmpty", "isBlank", "!blank", "isWithin", "isNotWithin", "notWithin"})
    void resolve_throwsForAnyOtherFuncOnANullANamingItAndPointingAtIsNull(String func) {
        // when / then
        assertThatThrownBy(() -> rule.resolve(new Condition(func, null, "x")))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("'" + canonical(func) + "'")
                .hasMessageContaining("null")
                .hasMessageContaining("isNull")
                .hasMessageContaining("isNotNull");
    }

    private static String canonical(String func) {
        return switch (func) {
            case "isEmpty", "isNotEmpty" -> "isEmpty";
            case "isBlank", "!blank" -> "isBlank";
            default -> "isWithin";
        };
    }

    @Test
    void resolve_doesNotAskAcceptsOrAPredicateForANullA() {
        // when
        assertThatThrownBy(() -> rule.resolve(new Condition("isWithin", null, "x")))
                .isInstanceOf(BratException.class);
        rule.resolve(new Condition("isNull", null, null));

        // then — the predicates throw if called
        assertThat(rule.calls).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"isNotEqulTo", "isBollocks"})
    void resolve_declinesAFuncItDoesNotKnowForANullA(String func) {
        // when
        var result = rule.resolve(new Condition(func, null, "x"));

        // then
        assertThat(result).isEmpty();
    }
}
