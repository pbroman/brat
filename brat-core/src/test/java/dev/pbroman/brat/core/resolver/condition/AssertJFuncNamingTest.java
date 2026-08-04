package dev.pbroman.brat.core.resolver.condition;

import java.util.List;

import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.resolver.condition.rules.BooleanConditionResolverRule;
import dev.pbroman.brat.core.resolver.condition.rules.DateConditionResolverRule;
import dev.pbroman.brat.core.resolver.condition.rules.FormatConditionResolverRule;
import dev.pbroman.brat.core.resolver.condition.rules.NullConditionResolverRule;
import dev.pbroman.brat.core.resolver.condition.rules.NumberConditionResolverRule;
import dev.pbroman.brat.core.resolver.condition.rules.StringConditionResolverRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 6, step 1: AssertJ-aligned func names, the {@code is}-before-negation normalisation order,
 * and type-inferred dispatch across categories. Red until step 1 is implemented.
 */
class AssertJFuncNamingTest {

    private ConditionResolverRuleDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new ConditionResolverRuleDispatcher(List.of(
                new BooleanConditionResolverRule(),
                new FormatConditionResolverRule(),
                new NumberConditionResolverRule(),
                new NullConditionResolverRule(),
                new DateConditionResolverRule(),
                new StringConditionResolverRule()));
    }

    // --- canonical AssertJ names ---

    @Test
    void resolve_resolvesIsEqualToOnStrings() {
        // when / then
        assertThat(dispatcher.resolve(new Condition("isEqualTo", "abc", "abc"))).isTrue();
        assertThat(dispatcher.resolve(new Condition("isEqualTo", "abc", "abd"))).isFalse();
    }

    @Test
    void resolve_resolvesIsGreaterThanAndFriendsOnNumbers() {
        // when / then
        assertThat(dispatcher.resolve(new Condition("isGreaterThan", "5", "3"))).isTrue();
        assertThat(dispatcher.resolve(new Condition("isLessThan", "5", "3"))).isFalse();
        assertThat(dispatcher.resolve(new Condition("isGreaterThanOrEqualTo", "5", "5")))
                .isTrue();
        assertThat(dispatcher.resolve(new Condition("isLessThanOrEqualTo", "5", "5")))
                .isTrue();
    }

    @Test
    void resolve_resolvesIsEqualToIgnoringCase() {
        // when / then
        assertThat(dispatcher.resolve(new Condition("isEqualToIgnoringCase", "ABC", "abc")))
                .isTrue();
    }

    @Test
    void resolve_matchesFuncNamesCaseInsensitively() {
        // when / then
        assertThat(dispatcher.resolve(new Condition("ISEQUALTO", "abc", "abc"))).isTrue();
        assertThat(dispatcher.resolve(new Condition("isequalto", "abc", "abc"))).isTrue();
    }

    // --- the normalisation-order bug: is-prefix stripped before negation is matched ---

    @Test
    void resolve_resolvesIsNotEqualTo() {
        // when / then
        assertThat(dispatcher.resolve(new Condition("isNotEqualTo", "abc", "abd")))
                .isTrue();
        assertThat(dispatcher.resolve(new Condition("isNotEqualTo", "abc", "abc")))
                .isFalse();
    }

    @Test
    void resolve_resolvesIsNotNull() {
        // when / then
        assertThat(dispatcher.resolve(new Condition("isNotNull", "abc", null))).isTrue();
        assertThat(dispatcher.resolve(new Condition("isNotNull", null, null))).isFalse();
    }

    @Test
    void resolve_stillResolvesTheBareNegationForms() {
        // when / then
        assertThat(dispatcher.resolve(new Condition("notContains", "abcdef", "xyz")))
                .isTrue();
        assertThat(dispatcher.resolve(new Condition("!contains", "abcdef", "xyz")))
                .isTrue();
    }

    // --- type-inferred dispatch, and the escapes that override it ---

    @Test
    void resolve_dispatchesIsEqualToOnNumericOperandsToTheNumberRule() {
        // given — the same number written two ways: equal numerically, unequal as strings

        // when / then
        assertThat(dispatcher.resolve(new Condition("isEqualTo", "1.50", "1.5")))
                .isTrue();
    }

    @Test
    void resolve_fallsBackToTheStringRuleWhenTheOperandsAreNotNumeric() {
        // when / then
        assertThat(dispatcher.resolve(new Condition("isEqualTo", "1.50", "abc")))
                .isFalse();
        assertThat(dispatcher.resolve(new Condition("isEqualTo", "abc", "abc"))).isTrue();
    }

    @Test
    void resolve_keepsEqualsAsAnExactStringComparison() {
        // when / then — the escape: `equals` never means numeric equality
        assertThat(dispatcher.resolve(new Condition("equals", "1.50", "1.5"))).isFalse();
    }

    @Test
    void resolve_keepsTheSymbolicOperatorsNumeric() {
        // when / then
        assertThat(dispatcher.resolve(new Condition("=", "1.50", "1.5"))).isTrue();
        assertThat(dispatcher.resolve(new Condition(">", "5", "3"))).isTrue();
    }

    @Test
    void resolve_throwsWhenNoRuleAcceptsTheOperands() {
        // when / then — a numeric-only func on non-numeric operands is nobody's to answer
        assertThatThrownBy(() -> dispatcher.resolve(new Condition("isGreaterThan", "abc", "def")))
                .isInstanceOf(BratException.class);
    }
}
