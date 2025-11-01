package dev.pbroman.brat.core.resolver.condition.rules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.pbroman.brat.core.api.resolver.ConditionResolver;
import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.exception.ValidationException;

class NullConditionResolverRuleTest {

    ConditionResolver resolver = new NullConditionResolverRule();

    @Test
    void isTrue() throws ValidationException {
        // when
        var result = resolver.resolve(new Condition("isNull", null, null));

        // then
        assertThat(result).isTrue();
    }

    @Test
    void isFalse() throws ValidationException {
        // when
        var result = resolver.resolve(new Condition("!null", null, null));

        // then
        assertThat(result).isFalse();
    }

    @Test
    void isNotForMe() throws ValidationException {
        // when
        var result = resolver.resolve(new Condition("null", "nonNullValue", null));

        // then
        assertThat(result).isNull();
    }

}