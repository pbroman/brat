package dev.pbroman.brat.core.data;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NullToleranceTest {

    @Test
    void constructor_treatsANullSeverityAsFail() {
        // when - an explicitly-null severity: key must not leave an assertion without one
        var assertion = new Assertion("isEqualTo", "a", "b", null, null, null, null);

        // then
        assertThat(assertion.getSeverity()).isEqualTo(AssertionSeverity.FAIL);
    }

    @Test
    void constructor_treatsNullArgsAsNoArgumentsOnACondition() {
        // when
        var condition = new Condition("isNull", "a", null, null);

        // then
        assertThat(condition.getArgs()).isEmpty();
    }

    @Test
    void constructor_treatsNullArgsAsNoArgumentsOnAnAssertion() {
        // when - `args:` written with nothing after it binds null
        var assertion = new Assertion("isEqualTo", "a", "b", null, null, null, null);

        // then
        assertThat(assertion.getArgs()).isEmpty();
    }

    @Test
    void constructor_treatsNullArgsAsNoArgumentsOnAChainLink() {
        // when
        var link = new ChainedCondition("contains", "x", null, null);

        // then
        assertThat(link.getArgs()).isEmpty();
    }

    @Test
    void constructor_treatsNullAssertionsAndSetVarsAsNone() {
        // when
        var actions = new ResponseActions(null, null);

        // then
        assertThat(actions.getAssertions()).isEmpty();
        assertThat(actions.getSetVars()).isEmpty();
    }

    @Test
    void constructor_treatsANullChainAsEmpty() {
        // when
        var assertion = new Assertion("isNotNull", "a", null, (List<ChainedCondition>) null, null);

        // then
        assertThat(assertion.getChain()).isEmpty();
    }
}
