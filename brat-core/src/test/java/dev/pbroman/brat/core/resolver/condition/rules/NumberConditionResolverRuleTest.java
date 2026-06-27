package dev.pbroman.brat.core.resolver.condition.rules;

import static dev.pbroman.brat.core.util.Constants.EQUAL_TO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.exception.BratException;

class NumberConditionResolverRuleTest extends AbstractConditionResolverRuleTest {

    @BeforeEach
    void setUp() {
        resolver = new NumberConditionResolverRule();
    }

    @ParameterizedTest
    @CsvSource({
            "=,1,1",
            "=,1,1.0",
            ">=,1,1",
            ">=,2,1",
            ">,2,1",
            "<=,1,1",
            "<=,1,2",
            "<,1,2",
    })
    void trueConditions(String func, String a, String b) {
        // given
        var condition = new Condition(func, a, b);

        // when
        var result = resolver.resolve(condition);

        // then
        assertThat(result).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "=,1,1.1",
            ">=,1,2",
            ">,1,2",
            "<=,2,1",
            "<,2,1",
    })
    void falseConditions(String func, String a, String b) {
        // given
        var condition = new Condition(func, a, b);

        // when
        var result = resolver.resolve(condition);

        // then
        assertThat(result).isFalse();
    }

    @Test
    void unparsableNumber() {
        // given
        var condition = new Condition(EQUAL_TO, 1, "noNumber");

        // then
        assertThatThrownBy(() -> resolver.resolve(condition))
                .isInstanceOf(BratException.class);
    }

}