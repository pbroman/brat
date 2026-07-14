package dev.pbroman.brat.core.reporting.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;

class UnittestReportingRuleTest {

    UnittestReportingRule underTest = new UnittestReportingRule();

    @Test
    void report_returnsNullForOtherKinds() {
        assertThat(underTest.report("console", Map.of())).isNull();
    }

    @Test
    void report_isBracketedCommaSeparated() {
        // given
        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();
        outcomes.put("url", new InterpolationOutcome("resolved", "${x} → resolved"));
        outcomes.put("method", new InterpolationOutcome("GET", "GET"));

        // when
        var result = underTest.report("unittest", outcomes);

        // then
        assertThat(result).isEqualTo("[url=${x} → resolved, method=GET]");
    }

}
