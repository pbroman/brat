package dev.pbroman.brat.core.resolver.condition.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import dev.pbroman.brat.core.api.resolver.ConditionResolver;
import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.exception.BratException;

abstract class AbstractConditionResolverRuleTest {

    protected ConditionResolver resolver;

    @Test
    void isNotForMe() {
        // when
        var result = resolver.resolve(new Condition("isBollocks", false, null));

        // then
        assertThat(result).isNull();
    }

    @Test
    void checksConditionIsNotNull() {
        assertThatThrownBy(() -> resolver.resolve(null))
                .isInstanceOf(BratException.class);
    }

}
