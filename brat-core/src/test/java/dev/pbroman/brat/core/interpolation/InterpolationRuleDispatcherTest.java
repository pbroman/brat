package dev.pbroman.brat.core.interpolation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.api.interpolation.InterpolationRule;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;

class InterpolationRuleDispatcherTest {

    private final RuntimeData runtimeData = new RuntimeData(Map.of(), Map.of());

    @Test
    void outcome_returnsInputUnchangedWhenNoRuleMatches() {
        // given
        InterpolationRule passthrough = (input, data) -> new InterpolationOutcome(input, input);
        var dispatcher = new InterpolationRuleDispatcher(List.of(passthrough));

        // when
        var outcome = dispatcher.outcome("${bollocks}", runtimeData);

        // then
        assertThat(outcome.value()).isEqualTo("${bollocks}");
        assertThat(outcome.reportingString()).isEqualTo("${bollocks}");
        assertThat(outcome.containsSecret()).isFalse();
    }

    @Test
    void outcome_resolvesThroughMultipleChainedRules() {
        // given
        InterpolationRule expandsShorthand = (input, data) ->
                input.equals("${rb}")
                        ? new InterpolationOutcome("${response.body}", "expanded")
                        : new InterpolationOutcome(input, input);
        InterpolationRule resolvesExpanded = (input, data) ->
                input.equals("${response.body}")
                        ? new InterpolationOutcome("myBody", "resolved")
                        : new InterpolationOutcome(input, input);
        var dispatcher = new InterpolationRuleDispatcher(List.of(expandsShorthand, resolvesExpanded));

        // when
        var outcome = dispatcher.outcome("${rb}", runtimeData);

        // then
        assertThat(outcome.value()).isEqualTo("myBody");
        assertThat(outcome.reportingString()).isEqualTo("${rb} → myBody");
        assertThat(outcome.containsSecret()).isFalse();
    }

    @Test
    void outcome_marksSecretWhenAnyRuleInChainDidAndMasksReportingString() {
        // given
        InterpolationRule resolvesSecret = (input, data) ->
                input.equals("${secrets.token}")
                        ? new InterpolationOutcome("abc123", "${secrets.token} → ***", true)
                        : new InterpolationOutcome(input, input);
        InterpolationRule wrapsResult = (input, data) -> new InterpolationOutcome("Bearer " + input, "wrapped");
        var dispatcher = new InterpolationRuleDispatcher(List.of(resolvesSecret, wrapsResult));

        // when
        var outcome = dispatcher.outcome("${secrets.token}", runtimeData);

        // then
        assertThat(outcome.value()).isEqualTo("Bearer abc123");
        assertThat(outcome.reportingString()).isEqualTo("${secrets.token} → ***");
        assertThat(outcome.containsSecret()).isTrue();
    }

    @Test
    void outcome_propagatesExceptionFromAnyRuleInChain() {
        // given
        InterpolationRule throwingRule = (input, data) -> {
            throw new BratException("boom");
        };
        var dispatcher = new InterpolationRuleDispatcher(List.of(throwingRule));

        // then
        assertThatThrownBy(() -> dispatcher.outcome("${x}", runtimeData))
                .isInstanceOf(BratException.class);
    }

}
