package dev.pbroman.brat.core.data;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AssertionSeverityTest {

    @Test
    void severity_defaultsToFailWhenNotDeclared() {
        // when
        var assertion = new Assertion("isEqualTo", "a", "b");

        // then
        assertThat(assertion.getSeverity()).isEqualTo(AssertionSeverity.FAIL);
    }

    @Test
    void severity_isSettable() {
        // given
        var assertion = new Assertion("isEqualTo", "a", "b");

        // when
        assertion.setSeverity(AssertionSeverity.WARN);

        // then
        assertThat(assertion.getSeverity()).isEqualTo(AssertionSeverity.WARN);
    }

    @Test
    void values_areOrderedLeastSevereFirst() {
        // then
        assertThat(AssertionSeverity.WARN.ordinal()).isLessThan(AssertionSeverity.FAIL.ordinal());
    }
}
