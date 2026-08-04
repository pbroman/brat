package dev.pbroman.brat.core.reporting.rules;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
