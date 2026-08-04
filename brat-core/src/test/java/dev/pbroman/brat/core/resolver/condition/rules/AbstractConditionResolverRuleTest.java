package dev.pbroman.brat.core.resolver.condition.rules;

import dev.pbroman.brat.core.api.resolver.ConditionResolverRule;
import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

abstract class AbstractConditionResolverRuleTest {

    protected ConditionResolverRule resolver;

    @Test
    void isNotForMe() {
        // when
        var result = resolver.resolve(new Condition("isBollocks", false, null));

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void checksConditionIsNotNull() {
        assertThatThrownBy(() -> resolver.resolve(null)).isInstanceOf(BratException.class);
    }
}
