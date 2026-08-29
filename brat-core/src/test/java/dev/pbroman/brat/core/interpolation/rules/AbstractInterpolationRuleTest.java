package dev.pbroman.brat.core.interpolation.rules;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AbstractInterpolationRuleTest {

    /**
     * The outcome of a rule that claimed the token, or one equal to the input where it declined —
     * the same two cases the dispatcher distinguishes.
     */
    private static InterpolationOutcome claimed(AbstractInterpolationRule rule, String input, RuntimeData data) {
        return rule.outcome(input, data).orElseGet(() -> new InterpolationOutcome(input, input));
    }

    private final RuntimeData runtimeData = new RuntimeData(Map.of(), Map.of());

    @Test
    void outcome_reportsSubstitutionWhenValueChanges() {
        // given
        var rule = new StubInterpolationRule(input -> "resolved");

        // when
        var outcome = claimed(rule, "${stub.key}", runtimeData);

        // then
        assertThat(outcome.value()).isEqualTo("resolved");
        assertThat(outcome.reportingString()).isEqualTo("${stub.key} → resolved");
    }

    @Test
    void outcome_reportsInputUnchangedWhenNothingWasSubstituted() {
        // given
        var rule = new StubInterpolationRule(Function.identity());

        // when
        var outcome = claimed(rule, "${stub.key}", runtimeData);

        // then
        assertThat(outcome.value()).isEqualTo("${stub.key}");
        assertThat(outcome.reportingString()).isEqualTo("${stub.key}");
    }

    @Test
    void outcome_propagatesExceptionFromResolve() {
        // given
        var rule = new StubInterpolationRule(input -> {
            throw new BratException("boom");
        });

        // then
        assertThatThrownBy(() -> claimed(rule, "${stub.key}", runtimeData)).isInstanceOf(BratException.class);
    }

    private static final class StubInterpolationRule extends AbstractInterpolationRule {

        private final Function<String, String> resolveFunction;

        StubInterpolationRule(Function<String, String> resolveFunction) {
            super("stub");
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
        var rule = new FallbackStubInterpolationRule(Map.of("threadCount", "5"));

        // when
        var result = claimed(rule, "${stub.threadCount}", runtimeData);

        // then
        assertThat(result.value()).isEqualTo("5");
    }

    @Test
    void simpleInterpolation_fallsBackToLiteralDefault() {
        // given
        var rule = new FallbackStubInterpolationRule(Map.of());

        // when
        var result = claimed(rule, "${stub.threadCount:-10}", runtimeData);

        // then
        assertThat(result.value()).isEqualTo("10");
    }

    @Test
    void simpleInterpolation_fallsBackToAnotherNamespace() {
        // given
        var rule = new FallbackStubInterpolationRule(Map.of());
        var data = new RuntimeData(Map.of(), Map.of("threadCount", "7"));

        // when
        var result = claimed(rule, "${stub.threadCount:-env.threadCount}", data);

        // then
        assertThat(result.value()).isEqualTo("7");
    }

    @Test
    void simpleInterpolation_chainsMultipleFallbacksLeftToRight() {
        // given
        var rule = new FallbackStubInterpolationRule(Map.of());
        var data = new RuntimeData(Map.of(), Map.of());

        // when
        var result = claimed(rule, "${stub.threadCount:-env.threadCount:-10}", data);

        // then
        assertThat(result.value()).isEqualTo("10");
    }

    @Test
    void simpleInterpolation_treatsUnrecognizedNamespaceSegmentAsLiteral() {
        // given
        var rule = new FallbackStubInterpolationRule(Map.of());

        // when
        var result = claimed(rule, "${stub.threadCount:-notANamespace.thing}", runtimeData);

        // then
        assertThat(result.value()).isEqualTo("notANamespace.thing");
    }

    @Test
    void simpleInterpolation_skipsAFallbackSegmentWhoseNamespaceIsAbsent() {
        // given a runtime with no constants namespace at all, not merely one lacking the key
        var rule = new FallbackStubInterpolationRule(Map.of());
        var data = new RuntimeData(null, Map.of());

        // when
        var result = claimed(rule, "${stub.threadCount:-constants.threadCount:-10}", data);

        // then
        assertThat(result.value()).isEqualTo("10");
    }

    @Test
    void simpleInterpolation_returnsInputWhenBlank() {
        // given
        var rule = new FallbackStubInterpolationRule(Map.of("threadCount", "5"));

        // when - resolve directly, not through outcome: a blank string is not a token, so claims
        // would decline it and this short-circuit would never be reached. It exists for an extender
        // calling simpleInterpolation from their own resolve, which is the path tested here
        var result = rule.resolve("   ", runtimeData);

        // then
        assertThat(result).isEqualTo("   ");
    }

    @Test
    void simpleInterpolation_returnsInputWhenValuesIsNull() {
        // given
        var rule = new FallbackStubInterpolationRule(null);

        // when - through outcome, unlike the two below: this is the one short-circuit a claimed
        // token still reaches, since the rule owns the namespace and only its values are missing
        var result = claimed(rule, "${stub.threadCount}", runtimeData);

        // then
        assertThat(result.value()).isEqualTo("${stub.threadCount}");
    }

    @Test
    void simpleInterpolation_returnsInputWhenTheNamespaceDoesNotMatch() {
        // given
        var rule = new FallbackStubInterpolationRule(Map.of("threadCount", "5"));

        // when - resolve directly, for the same reason as the blank case above: the default claims
        // matches on this very pattern, so outcome would decline before resolve ran
        var result = rule.resolve("${other.threadCount}", runtimeData);

        // then
        assertThat(result).isEqualTo("${other.threadCount}");
    }

    @Test
    void simpleInterpolation_throwsWhenChainExhaustedWithNoLiteral() {
        // given
        var rule = new FallbackStubInterpolationRule(Map.of());
        var data = new RuntimeData(Map.of(), Map.of());

        // then
        assertThatThrownBy(() -> rule.outcome("${stub.threadCount:-env.threadCount}", data))
                .isInstanceOf(BratException.class);
    }

    @Test
    void claims_recognizesATokenOfItsOwnNamespace() {
        // given
        var rule = new StubInterpolationRule(Function.identity());

        // when / then
        assertThat(rule.claims("${stub.key}")).isTrue();
    }

    @Test
    void claims_rejectsATokenOfAnotherNamespace() {
        // given
        var rule = new StubInterpolationRule(Function.identity());

        // when / then
        assertThat(rule.claims("${other.key}")).isFalse();
    }

    @Test
    void claims_rejectsTextThatIsNotAToken() {
        // given
        var rule = new StubInterpolationRule(Function.identity());

        // when / then
        assertThat(rule.claims("stub.key")).isFalse();
        assertThat(rule.claims("")).isFalse();
    }

    @Test
    void outcome_declinesWithoutResolvingWhenClaimsRejects() {
        // given - resolve must not see a token of another namespace
        var resolveCalls = new AtomicInteger();
        var rule = new StubInterpolationRule(input -> {
            resolveCalls.incrementAndGet();
            return "resolved";
        });

        // when
        var outcome = rule.outcome("${other.key}", runtimeData);

        // then
        assertThat(outcome).isEmpty();
        assertThat(resolveCalls).hasValue(0);
    }

    @Test
    void outcome_resolvesWhenClaimsAccepts() {
        // given
        var rule = new StubInterpolationRule(input -> "resolved");

        // when
        var outcome = rule.outcome("${stub.key}", runtimeData);

        // then
        assertThat(outcome).isPresent();
        assertThat(outcome.orElseThrow().value()).isEqualTo("resolved");
    }

    @Test
    void claims_isOverridableForANamespaceThatIsNotKeyDotRest() {
        // given - a bare token carrying no key, which the default shape cannot match
        var rule = new BareTokenStubInterpolationRule();

        // when / then
        assertThat(rule.claims("${bare}")).isTrue();
        assertThat(rule.outcome("${bare}", runtimeData)).isPresent();
        assertThat(rule.outcome("${other}", runtimeData)).isEmpty();
    }

    /** A rule whose token has no {@code .key} part, so it must decide ownership for itself. */
    private static final class BareTokenStubInterpolationRule extends AbstractInterpolationRule {

        BareTokenStubInterpolationRule() {
            super("bare");
        }

        @Override
        protected boolean claims(String input) {
            return ("${" + interpolationKey + "}").equals(input);
        }

        @Override
        protected String resolve(String input, RuntimeData runtimeData) {
            return "bareValue";
        }
    }

    private static final class FallbackStubInterpolationRule extends AbstractInterpolationRule {

        private final Map<String, ?> values;

        FallbackStubInterpolationRule(Map<String, ?> values) {
            super("stub");
            this.values = values;
        }

        @Override
        protected String resolve(String input, RuntimeData runtimeData) {
            return simpleInterpolation(input, runtimeData, values);
        }

        @Override
        protected String onMissingReplacement(String placeholder, String input, RuntimeData runtimeData) {
            throw new BratException("The stub value '" + placeholder + "' is not set.");
        }
    }
}
