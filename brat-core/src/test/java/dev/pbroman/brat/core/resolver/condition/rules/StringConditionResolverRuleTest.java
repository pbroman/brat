package dev.pbroman.brat.core.resolver.condition.rules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.exception.ValidationException;

class StringConditionResolverRuleTest extends AbstractConditionResolverRuleTest {

    @BeforeEach
    void setUp() {
        resolver = new StringConditionResolverRule();
    }

    @ParameterizedTest
    @CsvSource({
            "equals,test,test",
            "equalsIgnoreCase,test,TEST",
            "startsWith,test,te",
            "endsWith,test,st",
            "matches,test,t.*t",
            "contains,test,es",
            "null,,",
            "empty,'',",
            "blank,' ',",
    })
    void trueConditions(String func, String a, String b) throws ValidationException {
        // given
        var condition = new Condition(func, a, b);

        // when
        var result = resolver.resolve(condition);

        // then
        assertThat(result).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "equals,test,other",
            "equalsIgnoreCase,test,other",
            "startsWith,test,st",
            "endsWith,test,te",
            "matches,test,T.*t",
            "contains,test,se",
            "null,' ',",
            "empty,' ',",
            "blank,test,",
    })
    void falseConditions(String func, String a, String b) throws ValidationException {
        // given
        var condition = new Condition(func, a, b);

        // when
        var result = resolver.resolve(condition);

        // then
        assertThat(result).isFalse();
    }

}