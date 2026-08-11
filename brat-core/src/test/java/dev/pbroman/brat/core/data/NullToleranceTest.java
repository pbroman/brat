package dev.pbroman.brat.core.data;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NullToleranceTest {

    @Test
    void setSeverity_treatsNullAsFail() {
        // given
        var assertion = new Assertion("isEqualTo", "a", "b");
        assertion.setSeverity(AssertionSeverity.WARN);

        // when
        assertion.setSeverity(null);

        // then - an explicitly-null severity: key cannot leave an assertion without one
        assertThat(assertion.getSeverity()).isEqualTo(AssertionSeverity.FAIL);
    }

    @Test
    void setArgs_treatsNullAsNoArguments() {
        // given
        var link = new ChainedCondition("contains", "x");
        link.setArgs(Map.of("offset", "0.01"));

        // when
        link.setArgs(null);

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
