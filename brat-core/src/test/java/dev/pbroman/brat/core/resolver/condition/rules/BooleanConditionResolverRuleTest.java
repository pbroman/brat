package dev.pbroman.brat.core.resolver.condition.rules;

import dev.pbroman.brat.core.data.Condition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(result).contains(true);
    }

    @Test
    void isFalse() {
        // when
        var result = resolver.resolve(new Condition("isFalse", false, null));

        // then
        assertThat(result).contains(true);
    }
}
