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
    void isNotForMe() {
        // when
        var result = resolver.resolve(new Condition("null", "nonNullValue", null));

        // then
        assertThat(result).isEmpty();
    }
}
