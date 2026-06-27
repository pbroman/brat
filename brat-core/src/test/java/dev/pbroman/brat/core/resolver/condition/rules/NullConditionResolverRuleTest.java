package dev.pbroman.brat.core.resolver.condition.rules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.pbroman.brat.core.api.resolver.ConditionResolver;
import dev.pbroman.brat.core.data.Condition;

class NullConditionResolverRuleTest {

    ConditionResolver resolver = new NullConditionResolverRule();

    @Test
    void isTrue() {
        // when
        var result = resolver.resolve(new Condition("isNull", null, null));

        // then
        assertThat(result).isTrue();
    }

    @Test
    void isFalse() {
        // when
        var result = resolver.resolve(new Condition("!null", null, null));

        // then
        assertThat(result).isFalse();
    }

    @Test
    void isNotForMe() {
        // when
        var result = resolver.resolve(new Condition("null", "nonNullValue", null));

        // then
        assertThat(result).isNull();
    }

}