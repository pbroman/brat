package dev.pbroman.brat.core.resolver.condition.rules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.pbroman.brat.core.data.Condition;

class BooleanConditionResolverRuleTest extends AbstractConditionResolverRuleTest {

    @BeforeEach
    void setUp() {
        resolver = new BooleanConditionResolverRule();
    }

    @Test
    void isTrue() {
        // when
        var result = resolver.resolve(new Condition("isTrue", true, null));

        // then
        assertThat(result).isTrue();
    }

    @Test
    void isFalse() {
        // when
        var result = resolver.resolve(new Condition("isFalse", false, null));

        // then
        assertThat(result).isTrue();
    }

}