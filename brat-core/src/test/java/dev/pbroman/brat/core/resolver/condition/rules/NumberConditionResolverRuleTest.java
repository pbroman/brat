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
import java.util.Map;

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
        assertThat(result).contains(true);
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
        assertThat(result).contains(false);
    }

    @Test
    void resolve_declinesAnOperandThatIsNotANumber() {
        // given — `isEqualTo` is shared with the string and date categories, so a non-numeric
        // operand is not this rule's to answer: it declines and the dispatcher tries the next rule
        var condition = new Condition(EQUAL_TO, 1, "noNumber");

        // when
        var result = resolver.resolve(condition);

        // then
        assertThat(result).isEmpty();
    }

    // --- funcs taking their parameters from the params bag ---

    private Condition withArgs(String func, Object a, Object b, Map<String, String> args) {
        var condition = new Condition(func, a, b);
        condition.setArgs(args);
        return condition;
    }

    @Test
    void resolve_isBetweenIsInclusiveOnBothBounds() {
        // when / then
        assertThat(resolver.resolve(withArgs("isBetween", "10", null, Map.of("min", "10", "max", "100")))).contains(true);
        assertThat(resolver.resolve(withArgs("isBetween", "100", null, Map.of("min", "10", "max", "100")))).contains(true);
        assertThat(resolver.resolve(withArgs("isBetween", "55", null, Map.of("min", "10", "max", "100")))).contains(true);
        assertThat(resolver.resolve(withArgs("isBetween", "9", null, Map.of("min", "10", "max", "100")))).contains(false);
    }

    @Test
    void resolve_isCloseToComparesWithinTheOffset() {
        // when / then
        assertThat(resolver.resolve(withArgs("isCloseTo", "0.51", "0.5", Map.of("offset", "0.01")))).contains(true);
        assertThat(resolver.resolve(withArgs("isCloseTo", "0.52", "0.5", Map.of("offset", "0.01")))).contains(false);
    }

    @Test
    void resolve_throwsWhenARequiredParameterIsMissing() {
        // when / then
        assertThatThrownBy(() -> resolver.resolve(withArgs("isBetween", "10", null, Map.of("min", "1"))))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("max");
    }

    @Test
    void resolve_throwsForAParameterTheFuncDoesNotKnow() {
        // when / then — the typo a typed field would have caught at load time
        assertThatThrownBy(() -> resolver.resolve(withArgs("isCloseTo", "0.5", "0.5", Map.of("ofset", "0.01"))))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("ofset");
    }

    // --- exact decimal comparison ---

    @Test
    void resolve_comparesByValueNotByScale() {
        // when / then — BigDecimal.equals would call these different; compareTo does not
        assertThat(resolver.resolve(new Condition("isEqualTo", "1.50", "1.5"))).contains(true);
        assertThat(resolver.resolve(new Condition("isEqualTo", "1.500", "1.5"))).contains(true);
    }

    @Test
    void resolve_doesNotLosePrecisionToBinaryFloatingPoint() {
        // given — as doubles this sum is 0.30000000000000004
        var condition = new Condition("isGreaterThan", "0.3", "0.1");

        // when / then
        assertThat(resolver.resolve(condition)).contains(true);
        assertThat(resolver.resolve(new Condition("isEqualTo", "0.1", "0.10"))).contains(true);
    }

    @Test
    void resolve_declinesValuesThatAreNotDecimals() {
        // when / then — NaN and Infinity are doubles but not decimals, so this rule is not theirs
        assertThat(resolver.resolve(new Condition("isEqualTo", "NaN", "NaN"))).isEmpty();
        assertThat(resolver.resolve(new Condition("isGreaterThan", "Infinity", "1"))).isEmpty();
    }

    @Test
    void resolve_toleratesSurroundingWhitespace() {
        // when / then
        assertThat(resolver.resolve(new Condition("isEqualTo", " 1.5 ", "1.5"))).contains(true);
    }

}
