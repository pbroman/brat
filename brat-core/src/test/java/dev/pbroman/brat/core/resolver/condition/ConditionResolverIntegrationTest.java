package dev.pbroman.brat.core.resolver.condition;

import static dev.pbroman.brat.core.util.Constants.BLANK;
import static dev.pbroman.brat.core.util.Constants.CONTAINS;
import static dev.pbroman.brat.core.util.Constants.EMPTY;
import static dev.pbroman.brat.core.util.Constants.ENDS_WITH;
import static dev.pbroman.brat.core.util.Constants.EQUALS;
import static dev.pbroman.brat.core.util.Constants.EQUALS_IGNORE_CASE;
import static dev.pbroman.brat.core.util.Constants.EQUAL_TO;
import static dev.pbroman.brat.core.util.Constants.GREATER_THAN;
import static dev.pbroman.brat.core.util.Constants.GREATER_THAN_OR_EQUAL;
import static dev.pbroman.brat.core.util.Constants.LESS_THAN;
import static dev.pbroman.brat.core.util.Constants.LESS_THAN_OR_EQUAL;
import static dev.pbroman.brat.core.util.Constants.MATCHES;
import static dev.pbroman.brat.core.util.Constants.NULL;
import static dev.pbroman.brat.core.util.Constants.STARTS_WITH;
import static org.apache.commons.lang3.BooleanUtils.FALSE;
import static org.apache.commons.lang3.BooleanUtils.TRUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.data.result.ValidationType;
import dev.pbroman.brat.core.exception.ValidationException;
import dev.pbroman.brat.core.resolver.condition.rules.BooleanConditionResolverRule;
import dev.pbroman.brat.core.resolver.condition.rules.DoubleConditionResolverRule;
import dev.pbroman.brat.core.resolver.condition.rules.NullConditionResolverRule;
import dev.pbroman.brat.core.resolver.condition.rules.StringConditionResolverRule;

class ConditionResolverIntegrationTest {

    ConditionResolverRuleDispatcher ruleDispatcher;

    @BeforeEach
    void setUp() {
        ruleDispatcher = new ConditionResolverRuleDispatcher(List.of(
                new BooleanConditionResolverRule(),
                new DoubleConditionResolverRule(),
                new NullConditionResolverRule(),
                new StringConditionResolverRule()
        ));
    }

    @ParameterizedTest
    @MethodSource("happyPaths")
    void conditionIsTrue(Condition condition) throws ValidationException {
        assertThat(ruleDispatcher.resolve(condition)).isTrue();
    }

    @Test
    void functionNotFound() {
        assertThatThrownBy(
                () -> ruleDispatcher.resolve(new Condition("bollocks", 1, 2))
        ).isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("validationType", ValidationType.FAIL);
    }

    private static Stream<Arguments> happyPaths() {
        return Stream.of(
                // String conditions 
                Arguments.of(new Condition(NULL, null, null)),
                Arguments.of(new Condition("isnull", null, null)),
                Arguments.of(new Condition("notNull", "a", null)),
                Arguments.of(new Condition("notisNull", "", null)),
                Arguments.of(new Condition(EMPTY, "", null)),
                Arguments.of(new Condition("isEmpty", "", null)),
                Arguments.of(new Condition("notEmpty", "a", null)),
                Arguments.of(new Condition("!empty", "a", null)),
                Arguments.of(new Condition("!isEmpty", "a", null)),
                Arguments.of(new Condition(EQUALS, "foo", "foo")),
                Arguments.of(new Condition("notEquals", "foo", "bar")),
                Arguments.of(new Condition("!equals", "foo", "bar")),
                Arguments.of(new Condition(STARTS_WITH, "foo", "fo")),
                Arguments.of(new Condition("!startsWith", "foo", "a")),
                Arguments.of(new Condition(ENDS_WITH, "foo", "oo")),
                Arguments.of(new Condition(MATCHES, "foo", "f.*")),
                Arguments.of(new Condition("notMatches", "foo", "b.*")),
                Arguments.of(new Condition("!matches", "foo", "b.*")),
                Arguments.of(new Condition(CONTAINS, "foo", "o")),
                Arguments.of(new Condition("notContains", "foo", "a")),
                Arguments.of(new Condition("!contains", "foo", "a")),
                Arguments.of(new Condition(EQUALS_IGNORE_CASE, "foo", "fOO")),
                Arguments.of(new Condition(BLANK, "   ", null)),
                Arguments.of(new Condition("ISBLANK", "   ", null)),
                Arguments.of(new Condition("!blank", "foo", null)),
                Arguments.of(new Condition("notblank", "foo", null)),
                Arguments.of(new Condition("notisblank", "foo", null)),
                
                // Integer conditions
                Arguments.of(new Condition("!null", 1, null)),
                Arguments.of(new Condition(EQUAL_TO, 1, 1)),
                Arguments.of(new Condition("!=", 1, 2)),
                Arguments.of(new Condition(GREATER_THAN_OR_EQUAL, 1, 1)),
                Arguments.of(new Condition(GREATER_THAN_OR_EQUAL, 2, 1)),
                Arguments.of(new Condition(LESS_THAN_OR_EQUAL, 1, 1)),
                Arguments.of(new Condition(LESS_THAN_OR_EQUAL, 1, 2)),
                Arguments.of(new Condition(GREATER_THAN, 2, 1)),
                Arguments.of(new Condition(LESS_THAN, 1, 2)),

                // Double conditions
                Arguments.of(new Condition("notnull", 1.0, null)),
                Arguments.of(new Condition(EQUAL_TO, 1.0, 1.0)),
                Arguments.of(new Condition("!=", 1.0, 1.1)),
                Arguments.of(new Condition(GREATER_THAN_OR_EQUAL, 1.0, 1.0)),
                Arguments.of(new Condition(GREATER_THAN_OR_EQUAL, 1.1, 1.0)),
                Arguments.of(new Condition(LESS_THAN_OR_EQUAL, 1.0, 1.0)),
                Arguments.of(new Condition(LESS_THAN_OR_EQUAL, 1.0, 1.1)),
                Arguments.of(new Condition(GREATER_THAN, 1.1, 1.0)),
                Arguments.of(new Condition(LESS_THAN, 1.0, 1.1)),

                // Boolean conditions
                Arguments.of(new Condition("notNull", Boolean.TRUE, null)),
                Arguments.of(new Condition(TRUE, Boolean.TRUE, null)),
                Arguments.of(new Condition(FALSE, Boolean.FALSE, null)),

                // Mixed conditions
                Arguments.of(new Condition(EQUALS,"1", 1)),
                Arguments.of(new Condition(EQUALS, "true", Boolean.TRUE)),
                Arguments.of(new Condition(EQUALS, "1.0", 1.0)),
                Arguments.of(new Condition(EQUALS,1, "1")),
                Arguments.of(new Condition(EQUALS, Boolean.TRUE, "true")),
                Arguments.of(new Condition(EQUALS, 1.0, "1.0")),
                Arguments.of(new Condition(EQUAL_TO, 1.0, "1.0")),
                Arguments.of(new Condition(EQUAL_TO, 1.0, 1)),
                Arguments.of(new Condition(TRUE, "true", null))

        );
    }
}