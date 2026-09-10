package dev.pbroman.brat.core.interpolation;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.api.interpolation.InterpolationRule;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The first-match dispatch contract of {@link InterpolationRuleDispatcher}.
 */
class InterpolationRuleDispatcherFirstMatchTest {

    private final RuntimeData runtimeData = new RuntimeData(Map.of(), Map.of());

    @Test
    void outcome_returnsTheFirstClaimingRulesOutcome() {
        // given
        var dispatcher = new InterpolationRuleDispatcher(List.of(claiming("first", 0), claiming("second", 0)));

        // when
        var outcome = dispatcher.outcome("${x}", runtimeData);

        // then
        assertThat(outcome.asString()).isEqualTo("first");
    }

    @Test
    void outcome_doesNotConsultRulesBelowTheClaimingOne() {
        // given
        var callsBelow = new AtomicInteger();
        var dispatcher = new InterpolationRuleDispatcher(List.of(claiming("first", 10), counting(callsBelow)));

        // when
        dispatcher.outcome("${x}", runtimeData);

        // then
        assertThat(callsBelow).hasValue(0);
    }

    @Test
    void outcome_skipsDecliningRules() {
        // given
        var dispatcher = new InterpolationRuleDispatcher(List.of(declining(), declining(), claiming("third", 0)));

        // when
        var outcome = dispatcher.outcome("${x}", runtimeData);

        // then
        assertThat(outcome.asString()).isEqualTo("third");
    }

    @Test
    void outcome_consultsRulesInPriorityOrder() {
        // given - declared lowest-first, so only priority can produce "high"
        var dispatcher = new InterpolationRuleDispatcher(List.of(claiming("low", 1), claiming("high", 50)));

        // when
        var outcome = dispatcher.outcome("${x}", runtimeData);

        // then
        assertThat(outcome.asString()).isEqualTo("high");
    }

    @Test
    void outcome_returnsInputUnchangedWhenNoRuleClaims() {
        // given
        var dispatcher = new InterpolationRuleDispatcher(List.of(declining()));

        // when
        var outcome = dispatcher.outcome("${nobodys}", runtimeData);

        // then
        assertThat(outcome.asString()).isEqualTo("${nobodys}");
        assertThat(outcome.reportingString()).isEqualTo("${nobodys}");
    }

    @Test
    void outcome_reportsAValueEqualToItsOwnTokenAsUnchanged() {
        // given - a rule that claims the token and resolves it to its own text
        var claimsAndEchoes = rule(0, input -> Optional.of(new InterpolationOutcome(input, "reported")));
        var dispatcher = new InterpolationRuleDispatcher(List.of(claimsAndEchoes));

        // when
        var outcome = dispatcher.outcome("${vars.echo}", runtimeData);

        // then - indistinguishable in the report from a token nobody claimed, as documented
        assertThat(outcome.asString()).isEqualTo("${vars.echo}");
        assertThat(outcome.reportingString()).isEqualTo("${vars.echo}");
    }

    @Test
    void outcome_doesNotInterpolateAResolvedValue() {
        // given - the vulnerability the change closes: a claimed value that looks like another token
        var producesToken = rule(0, input -> Optional.of(new InterpolationOutcome("${secrets.apiKey}", "reported")));
        var secretRule = rule(
                0,
                input -> "${secrets.apiKey}".equals(input)
                        ? Optional.of(new InterpolationOutcome("SUPER-SECRET", "reported", true))
                        : Optional.empty());
        var dispatcher = new InterpolationRuleDispatcher(List.of(producesToken, secretRule));

        // when
        var outcome = dispatcher.outcome("${vars.fromResponse}", runtimeData);

        // then
        assertThat(outcome.asString()).isEqualTo("${secrets.apiKey}");
        assertThat(outcome.containsSecret()).isFalse();
    }

    @Test
    void outcome_carriesTheClaimingRulesSecretTag() {
        // given
        var dispatcher = new InterpolationRuleDispatcher(
                List.of(rule(0, input -> Optional.of(new InterpolationOutcome("v", "***", true)))));

        // when
        var outcome = dispatcher.outcome("${x}", runtimeData);

        // then
        assertThat(outcome.containsSecret()).isTrue();
        assertThat(outcome.reportingString()).isEqualTo("${x} → ***");
    }

    @Test
    void outcome_propagatesAnExceptionFromTheClaimingRule() {
        // given
        var callsBelow = new AtomicInteger();
        var throwing = rule(10, input -> {
            throw new BratException("boom");
        });
        var dispatcher = new InterpolationRuleDispatcher(List.of(throwing, counting(callsBelow)));

        // when / then - a rule that throws has claimed the token, so nothing below is tried
        assertThatThrownBy(() -> dispatcher.outcome("${x}", runtimeData)).isInstanceOf(BratException.class);
        assertThat(callsBelow).hasValue(0);
    }

    @Test
    void outcome_throwsForANullInput() {
        // given
        var dispatcher = new InterpolationRuleDispatcher(List.of(declining()));

        // when / then
        assertThatThrownBy(() -> dispatcher.outcome(null, runtimeData)).isInstanceOf(BratException.class);
    }

    private static InterpolationRule claiming(String value, int priority) {
        return rule(priority, input -> Optional.of(new InterpolationOutcome(value, "reported")));
    }

    private static InterpolationRule declining() {
        return rule(0, input -> Optional.empty());
    }

    private static InterpolationRule counting(AtomicInteger calls) {
        return rule(-10, input -> {
            calls.incrementAndGet();
            return Optional.empty();
        });
    }

    private static InterpolationRule rule(
            int priority, java.util.function.Function<String, Optional<InterpolationOutcome>> body) {
        return new InterpolationRule() {
            @Override
            public Optional<InterpolationOutcome> outcome(String input, RuntimeData runtimeData) {
                return body.apply(input);
            }

            @Override
            public int priority() {
                return priority;
            }
        };
    }
}
