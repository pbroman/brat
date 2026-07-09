package dev.pbroman.brat.core.interpolation.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.properties.InterpolationProperties;
import dev.pbroman.brat.core.tools.InterpolationTools;

class AbstractInterpolationRuleTest {

    private final InterpolationTools tools = new InterpolationTools(new InterpolationProperties());

    private final RuntimeData runtimeData = new RuntimeData(Map.of(), Map.of());

    @Test
    void outcome_reportsSubstitutionWhenValueChanges() {
        // given
        var rule = new StubInterpolationRule(tools, input -> "resolved");

        // when
        var outcome = rule.outcome("${stub.key}", runtimeData);

        // then
        assertThat(outcome.value()).isEqualTo("resolved");
        assertThat(outcome.reportingString()).isEqualTo("${stub.key} → resolved");
    }

    @Test
    void outcome_reportsInputUnchangedWhenNothingWasSubstituted() {
        // given
        var rule = new StubInterpolationRule(tools, Function.identity());

        // when
        var outcome = rule.outcome("${stub.key}", runtimeData);

        // then
        assertThat(outcome.value()).isEqualTo("${stub.key}");
        assertThat(outcome.reportingString()).isEqualTo("${stub.key}");
    }

    @Test
    void outcome_propagatesExceptionFromResolve() {
        // given
        var rule = new StubInterpolationRule(tools, input -> {
            throw new BratException("boom");
        });

        // then
        assertThatThrownBy(() -> rule.outcome("${stub.key}", runtimeData))
                .isInstanceOf(BratException.class);
    }

    private static final class StubInterpolationRule extends AbstractInterpolationRule {

        private final Function<String, String> resolveFunction;

        StubInterpolationRule(InterpolationTools tools, Function<String, String> resolveFunction) {
            super("stub", tools);
            this.resolveFunction = resolveFunction;
        }

        @Override
        protected String resolve(String input, RuntimeData runtimeData) {
            return resolveFunction.apply(input);
        }
    }

}
