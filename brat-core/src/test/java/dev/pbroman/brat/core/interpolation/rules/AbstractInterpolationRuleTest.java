package dev.pbroman.brat.core.interpolation.rules;

import java.util.Map;
import java.util.function.Function;

import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.interpolation.InterpolationPatterns;
import dev.pbroman.brat.core.interpolation.InterpolationProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AbstractInterpolationRuleTest {

    private final InterpolationPatterns patterns = new InterpolationPatterns(new InterpolationProperties());

    private final RuntimeData runtimeData = new RuntimeData(Map.of(), Map.of());

    @Test
    void outcome_reportsSubstitutionWhenValueChanges() {
        // given
        var rule = new StubInterpolationRule(patterns, input -> "resolved");

        // when
        var outcome = rule.outcome("${stub.key}", runtimeData);

        // then
        assertThat(outcome.value()).isEqualTo("resolved");
        assertThat(outcome.reportingString()).isEqualTo("${stub.key} → resolved");
    }

    @Test
    void outcome_reportsInputUnchangedWhenNothingWasSubstituted() {
        // given
        var rule = new StubInterpolationRule(patterns, Function.identity());

        // when
        var outcome = rule.outcome("${stub.key}", runtimeData);

        // then
        assertThat(outcome.value()).isEqualTo("${stub.key}");
        assertThat(outcome.reportingString()).isEqualTo("${stub.key}");
    }

    @Test
    void outcome_propagatesExceptionFromResolve() {
        // given
        var rule = new StubInterpolationRule(patterns, input -> {
            throw new BratException("boom");
        });

        // then
        assertThatThrownBy(() -> rule.outcome("${stub.key}", runtimeData)).isInstanceOf(BratException.class);
    }

    private static final class StubInterpolationRule extends AbstractInterpolationRule {

        private final Function<String, String> resolveFunction;

        StubInterpolationRule(InterpolationPatterns patterns, Function<String, String> resolveFunction) {
            super("stub", patterns);
            this.resolveFunction = resolveFunction;
        }

        @Override
        protected String resolve(String input, RuntimeData runtimeData) {
            return resolveFunction.apply(input);
        }
    }

    @Test
    void simpleInterpolation_resolvesDirectKeyWithoutFallback() {
        // given
        var rule = new FallbackStubInterpolationRule(patterns, Map.of("threadCount", "5"));

        // when
        var result = rule.outcome("${stub.threadCount}", runtimeData);

        // then
        assertThat(result.value()).isEqualTo("5");
    }

    @Test
    void simpleInterpolation_fallsBackToLiteralDefault() {
        // given
        var rule = new FallbackStubInterpolationRule(patterns, Map.of());

        // when
        var result = rule.outcome("${stub.threadCount:-10}", runtimeData);

        // then
        assertThat(result.value()).isEqualTo("10");
    }

    @Test
    void simpleInterpolation_fallsBackToAnotherNamespace() {
        // given
        var rule = new FallbackStubInterpolationRule(patterns, Map.of());
        var data = new RuntimeData(Map.of(), Map.of("threadCount", "7"));

        // when
        var result = rule.outcome("${stub.threadCount:-env.threadCount}", data);

        // then
        assertThat(result.value()).isEqualTo("7");
    }

    @Test
    void simpleInterpolation_chainsMultipleFallbacksLeftToRight() {
        // given
        var rule = new FallbackStubInterpolationRule(patterns, Map.of());
        var data = new RuntimeData(Map.of(), Map.of());

        // when
        var result = rule.outcome("${stub.threadCount:-env.threadCount:-10}", data);

        // then
        assertThat(result.value()).isEqualTo("10");
    }

    @Test
    void simpleInterpolation_treatsUnrecognizedNamespaceSegmentAsLiteral() {
        // given
        var rule = new FallbackStubInterpolationRule(patterns, Map.of());

        // when
        var result = rule.outcome("${stub.threadCount:-notANamespace.thing}", runtimeData);

        // then
        assertThat(result.value()).isEqualTo("notANamespace.thing");
    }

    @Test
    void simpleInterpolation_throwsWhenChainExhaustedWithNoLiteral() {
        // given
        var rule = new FallbackStubInterpolationRule(patterns, Map.of());
        var data = new RuntimeData(Map.of(), Map.of());

        // then
        assertThatThrownBy(() -> rule.outcome("${stub.threadCount:-env.threadCount}", data))
                .isInstanceOf(BratException.class);
    }

    private static final class FallbackStubInterpolationRule extends AbstractInterpolationRule {

        private final Map<String, ?> values;

        FallbackStubInterpolationRule(InterpolationPatterns patterns, Map<String, ?> values) {
            super("stub", patterns);
            this.values = values;
        }

        @Override
        protected String resolve(String input, RuntimeData runtimeData) {
            return simpleInterpolation(input, runtimeData, values);
        }

        @Override
        protected String onMissingReplacement(String placeholder, String input) {
            throw new BratException("The stub value '" + placeholder + "' is not set.");
        }
    }
}
