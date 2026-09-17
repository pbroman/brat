package dev.pbroman.brat.core.resolver.condition.rules;

import dev.pbroman.brat.core.data.Condition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NullConditionResolverRuleTest extends AbstractConditionResolverRuleTest {

    @BeforeEach
    void setUp() {
        resolver = new NullConditionResolverRule();
    }

    @Test
    void isTrue() {
        // when
        var result = resolver.resolve(new Condition("isNull", null, null));

        // then
        assertThat(result).contains(true);
    }

    @Test
    void isFalse() {
        // when
        var result = resolver.resolve(new Condition("!null", null, null));

        // then
        assertThat(result).contains(false);
    }

    @Test
    void resolve_returnsFalseForAFuncThatMerelyStartsLikeIsNull() {
        // then - a prefix of 'isNull' is not 'isNull'; it is a func this rule does not know, and a
        // null cannot satisfy one
        assertThat(resolver.resolve(new Condition("is", null, null))).contains(false);
        assertThat(resolver.resolve(new Condition("isNul", null, null))).contains(false);
    }

    @Test
    void resolve_matchesTheFuncIgnoringCase() {
        // then - every other rule lowercases the authored func, so this one may not be stricter
        assertThat(resolver.resolve(new Condition("ISNULL", null, null))).contains(true);
        assertThat(resolver.resolve(new Condition("Null", null, null))).contains(true);
    }

    @Test
    void isNotForMe() {
        // when
        var result = resolver.resolve(new Condition("null", "nonNullValue", null));

        // then
        assertThat(result).isEmpty();
    }
}
