package dev.pbroman.brat.core.reporting.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;

class VerboseCliReportingRuleTest {

    VerboseCliReportingRule underTest = new VerboseCliReportingRule();

    @Test
    void report_returnsNullForOtherKinds() {
        assertThat(underTest.report("console", Map.of())).isNull();
    }

    @Test
    void report_groupsByKeyPrefix() {
        // given
        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();
        outcomes.put("url", new InterpolationOutcome("resolved", "${x} → resolved"));
        outcomes.put("header.Authorization", new InterpolationOutcome("Bearer ***", "Bearer ${secrets.token} → Bearer ***"));
        outcomes.put("auth.type", new InterpolationOutcome("none", "none"));

        // when
        var result = underTest.report("verbose-cli", outcomes);

        // then
        assertThat(result)
                .contains("url: ${x} → resolved")
                .contains("Header:")
                .contains("  Authorization: Bearer ${secrets.token} → Bearer ***")
                .contains("Auth:")
                .contains("  type: none");
    }

}
