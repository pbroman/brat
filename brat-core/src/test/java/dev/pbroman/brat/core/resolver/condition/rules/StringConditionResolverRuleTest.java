package dev.pbroman.brat.core.resolver.condition.rules;

import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void trueConditions(String func, String a, String b) {
        // given
        var condition = new Condition(func, a, b);

        // when
        var result = resolver.resolve(condition);

        // then
        assertThat(result).contains(true);
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
    void falseConditions(String func, String a, String b) {
        // given
        var condition = new Condition(func, a, b);

        // when
        var result = resolver.resolve(condition);

        // then
        assertThat(result).contains(false);
    }

    @ParameterizedTest
    @CsvSource({
        "containsIgnoringCase,Content-Type,CONTENT",
        "containsIgnoringCase,content-type,Type",
        "isMediaType,application/json,application/json",
        "isMediaType,application/json; charset=utf-8,application/json",
        "isMediaType,application/json,application/json; charset=utf-8",
        "isMediaType,APPLICATION/JSON,application/json",
    })
    void resolve_isTrueForTheNewStringFuncs(String func, String a, String b) {
        // given
        var condition = new Condition(func, a, b);

        // when
        var result = resolver.resolve(condition);

        // then
        assertThat(result).contains(true);
    }

    @ParameterizedTest
    @CsvSource({
        "containsIgnoringCase,Content-Type,length",
        "isMediaType,application/json,text/plain",
        "isMediaType,application/json; charset=utf-8,application/xml",
    })
    void resolve_isFalseForTheNewStringFuncs(String func, String a, String b) {
        // given
        var condition = new Condition(func, a, b);

        // when
        var result = resolver.resolve(condition);

        // then
        assertThat(result).contains(false);
    }

    @Test
    void resolve_wrapsAFailureThatIsNotACategoryMismatch() {
        // given — this rule owns `matches` and accepts any operands, so a malformed regex is a
        // genuine failure rather than a reason to decline the condition
        var condition = new Condition("matches", "abc", "[");

        // when / then
        assertThatThrownBy(() -> resolver.resolve(condition)).isInstanceOf(BratException.class);
    }
}
