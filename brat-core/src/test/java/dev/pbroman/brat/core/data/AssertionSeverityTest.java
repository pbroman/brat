package dev.pbroman.brat.core.data;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssertionSeverityTest {

    @Test
    void severity_defaultsToFailWhenNotDeclared() {
        // when
        var assertion = new Assertion("isEqualTo", "a", "b");

        // then
        assertThat(assertion.getSeverity()).isEqualTo(AssertionSeverity.FAIL);
    }

    @Test
    void severity_isCarriedFromTheConstructor() {
        // when
        var assertion = new Assertion("isEqualTo", "a", "b", null, null, null, AssertionSeverity.WARN);

        // then
        assertThat(assertion.getSeverity()).isEqualTo(AssertionSeverity.WARN);
    }

    @Test
    void values_areOrderedLeastSevereFirst() {
        // then
        assertThat(AssertionSeverity.WARN.ordinal()).isLessThan(AssertionSeverity.FAIL.ordinal());
    }
}
