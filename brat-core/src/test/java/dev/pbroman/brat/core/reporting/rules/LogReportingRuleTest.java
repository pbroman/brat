package dev.pbroman.brat.core.reporting.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;

class LogReportingRuleTest {

    LogReportingRule underTest = new LogReportingRule();

    @Test
    void report_returnsNullForOtherKinds() {
        assertThat(underTest.report("console", Map.of())).isNull();
    }

    @Test
    void report_isLogfmtSingleLine() {
        // given
        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();
        outcomes.put("url", new InterpolationOutcome("resolved", "${x} → resolved"));
        outcomes.put("method", new InterpolationOutcome("GET", "GET"));

        // when
        var result = underTest.report("log", outcomes);

        // then
        assertThat(result).isEqualTo("url=\"${x} → resolved\" method=\"GET\"");
    }

    @Test
    void report_escapesQuotesInValues() {
        // given
        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();
        outcomes.put("body", new InterpolationOutcome("{\"a\":1}", "{\"a\":1}"));

        // when
        var result = underTest.report("log", outcomes);

        // then
        assertThat(result).isEqualTo("body=\"{\\\"a\\\":1}\"");
    }

    @Test
    void report_escapesBackslashesInValues() {
        // given
        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();
        outcomes.put("path", new InterpolationOutcome("C:\\path\\", "C:\\path\\"));

        // when
        var result = underTest.report("log", outcomes);

        // then the trailing backslash is doubled, so a parser reads it as an escaped backslash
        // followed by the real closing quote, not as an escaped quote
        assertThat(result).isEqualTo("path=\"C:\\\\path\\\\\"");
    }

}
