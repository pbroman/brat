package dev.pbroman.brat.core.resolver.condition.rules;

import static dev.pbroman.brat.core.util.Constants.EQUAL;
import static dev.pbroman.brat.core.util.Constants.PAST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.exception.BratException;

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
    void trueConditions(String func, String a, String b) {
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
                .isInstanceOf(BratException.class);
    }

    @Test
    void customDateTimeFormatterIsHonored() {
        // given
        var customFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH);
        var resolverWithCustomFormatter = new DateConditionResolverRule(customFormatter);
        var condition = new Condition(EQUAL, "Dec 21, 2002", "Dec 21, 2002");

        // when
        var result = resolverWithCustomFormatter.resolve(condition);

        // then
        assertThat(result).isTrue();
    }

    @Test
    void customDateTimeFormatterRejectsDefaultPatterns() {
        // given
        var customFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH);
        var resolverWithCustomFormatter = new DateConditionResolverRule(customFormatter);
        var condition = new Condition(PAST, "2002-12-21", null);

        // then
        assertThatThrownBy(() -> resolverWithCustomFormatter.resolve(condition))
                .isInstanceOf(BratException.class);
    }

}