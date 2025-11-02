package dev.pbroman.brat.core.resolver.condition.rules;

import static dev.pbroman.brat.core.util.Constants.PAST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.exception.ValidationException;

class DateConditionResolverRuleTest extends AbstractConditionResolverRuleTest {

    @BeforeEach
    void setUp() {
        resolver = new DateConditionResolverRule();
    }

    @ParameterizedTest
    @CsvSource({
            "equal,2002-12-21,2002-12-21",
            "equal,2002-12-21,21.12.2002",
            "equal,21.12.2002,12/21/2002",
            "equal,2002-12-21,12/21/2002",
            "before,2002-12-21,2002-12-22",
            "after,2002-12-22,2002-12-21",
            "past,2002-12-21,",
            "future,3002-12-21,",
            "!after,2002-12-21,2002-12-22",
            "!before,2002-12-22,2002-12-21",
    })
    void trueConditions(String func, String a, String b) throws ValidationException {
        // given
        var condition = new Condition(func, a, b);

        // when
        var result = resolver.resolve(condition);

        // then
        assertThat(result).isTrue();
    }

    @Test
    void unparsableDate() {
        // given
        var condition = new Condition(PAST, "20-12-23", null);

        // then
        assertThatThrownBy(() -> resolver.resolve(condition))
                .isInstanceOf(ValidationException.class);
    }

}