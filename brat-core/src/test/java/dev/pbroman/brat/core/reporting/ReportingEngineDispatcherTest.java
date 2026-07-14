package dev.pbroman.brat.core.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.api.reporting.ReportingRule;
import dev.pbroman.brat.core.exception.BratException;

class ReportingEngineDispatcherTest {

    @Test
    void report_returnsFirstMatchingRuleResult() {
        // given
        ReportingRule declining = (kind, outcomes) -> null;
        ReportingRule matching = (kind, outcomes) -> "matched";
        var underTest = new ReportingEngineDispatcher(List.of(declining, matching));

        // when
        var result = underTest.report("any", Map.of());

        // then
        assertThat(result).isEqualTo("matched");
    }

    @Test
    void report_higherPriorityRuleWinsOverLowerPriority() {
        // given
        ReportingRule low = new ReportingRule() {
            @Override
            public String report(String kind, Map<String, InterpolationOutcome> outcomes) {
                return "low";
            }
        };
        ReportingRule high = new ReportingRule() {
            @Override
            public int priority() {
                return 200;
            }

            @Override
            public String report(String kind, Map<String, InterpolationOutcome> outcomes) {
                return "high";
            }
        };
        var underTest = new ReportingEngineDispatcher(List.of(low, high));

        // when
        var result = underTest.report("any", Map.of());

        // then
        assertThat(result).isEqualTo("high");
    }

    @Test
    void report_throwsIfNoRuleMatches() {
        // given
        ReportingRule declining = (kind, outcomes) -> null;
        var underTest = new ReportingEngineDispatcher(List.of(declining));

        // when / then
        assertThatThrownBy(() -> underTest.report("any", Map.of()))
                .isInstanceOf(BratException.class);
    }

    @Test
    void report_throwsIfKindIsNull() {
        // given
        var underTest = new ReportingEngineDispatcher(List.of());

        // when / then
        assertThatThrownBy(() -> underTest.report(null, Map.of()))
                .isInstanceOf(BratException.class);
    }

}
