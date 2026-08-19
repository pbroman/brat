package dev.pbroman.brat.core.resolver.assertion;

import java.util.List;

import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.api.resolver.AssertionResolver;
import dev.pbroman.brat.core.api.resolver.ConditionResolver;
import dev.pbroman.brat.core.data.Assertion;
import dev.pbroman.brat.core.data.AssertionSeverity;
import dev.pbroman.brat.core.data.ChainedCondition;
import dev.pbroman.brat.core.data.result.AssertionResult;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.interpolation.configdata.AssertionInterpolator;
import dev.pbroman.brat.core.interpolation.configdata.ChainedConditionInterpolator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class AssertionChainResolverTest {

    Interpolation interpolation = Mockito.mock(Interpolation.class);
    ConditionResolver conditionResolver = Mockito.mock(ConditionResolver.class);
    RuntimeData runtimeData = Mockito.mock(RuntimeData.class);

    AssertionResolver assertionResolver = new AssertionChainResolver(
            interpolation, conditionResolver, new AssertionInterpolator(new ChainedConditionInterpolator()));

    @BeforeEach
    void setUp() {
        // Pass values through unchanged, so a message stays distinguishable from another message.
        // A message is interpolated like any other field, so a mock collapsing every value to one
        // string would make the message assertions below vacuous.
        // The null guard is for Mockito, not for production: re-stubbing with when(...) in a test
        // below invokes the mock once with null arguments, and an outcome rejects a null value.
        when(interpolation.outcome(any(), any())).thenAnswer(invocation -> {
            Object value = invocation.getArgument(0);
            return new InterpolationOutcome(value == null ? "" : value, "reported");
        });
    }

    @Test
    void happyPath() {
        // given
        var chain = List.of(new ChainedCondition("!equals", "c"));
        var assertion = new Assertion("a", "equals", "b", chain);
        when(conditionResolver.resolve(any())).thenReturn(true);

        // when
        var result = assertionResolver.resolve(assertion, runtimeData);

        // then
        assertThat(result).hasSize(2).allMatch(AssertionResult::passed);
    }

    @Test
    void resolve_defaultsEveryResultToFailSeverity() {
        // given
        var assertion = new Assertion("equals", "a", "b", List.of(new ChainedCondition("!equals", "c")));
        when(conditionResolver.resolve(any())).thenReturn(true);

        // when
        var result = assertionResolver.resolve(assertion, runtimeData);

        // then
        assertThat(result).hasSize(2).allMatch(r -> r.severity() == AssertionSeverity.FAIL);
    }

    @Test
    void resolve_carriesTheAssertionsSeverityOntoEveryResultInTheChain() {
        // given
        var assertion = new Assertion(
                "equals", "a", "b", List.of(new ChainedCondition("!equals", "c")), null, null, AssertionSeverity.WARN);
        when(conditionResolver.resolve(any())).thenReturn(true);

        // when
        var result = assertionResolver.resolve(assertion, runtimeData);

        // then
        assertThat(result).hasSize(2).allMatch(r -> r.severity() == AssertionSeverity.WARN);
    }

    @Test
    void resolve_carriesTheSeverityOntoAResultThatFailedToInterpolate() {
        // given
        var assertion = new Assertion("equals", "a", "b", null, null, null, AssertionSeverity.WARN);
        when(interpolation.outcome(any(), any())).thenThrow(new BratException("nope"));

        // when
        var result = assertionResolver.resolve(assertion, runtimeData);

        // then
        assertThat(result)
                .singleElement()
                .satisfies(r -> assertThat(r.severity()).isEqualTo(AssertionSeverity.WARN));
    }

    @Test
    void allAssertionsFail() {
        // given
        var chain = List.of(new ChainedCondition("!equals", "c", "chainedFail"));
        var assertion = new Assertion("equals", "a", "b", chain, "primaryFail");
        when(conditionResolver.resolve(any())).thenReturn(false);

        // when
        var result = assertionResolver.resolve(assertion, runtimeData);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.getFirst().message()).isEqualTo("primaryFail");
        assertThat(result.getLast().message()).isEqualTo("chainedFail");
    }

    @Test
    void resolve_convertsAnInterpolationFailureToOneFailedResult() {
        // given
        var chain = List.of(new ChainedCondition("!equals", "c", "chainedFail"));
        var assertion = new Assertion("equals", "a", "b", chain, "primaryFail");
        when(conditionResolver.resolve(any())).thenReturn(true);
        when(interpolation.outcome(any(), any())).thenThrow(new BratException("interpolation failed"));

        // when
        var result = assertionResolver.resolve(assertion, runtimeData);

        // then - the assertion is interpolated as a unit, so a failure yields a single result
        assertThat(result)
                .singleElement()
                .satisfies(r -> assertThat(r.message()).startsWith("Error interpolating"))
                .satisfies(r -> assertThat(r.passed()).isFalse());
    }

    @Test
    void resolve_convertsAResolutionFailureToAFailedResult() {
        // given — the shape an unrecognized func takes: ConditionResolverRuleDispatcher throws this
        var assertion = new Assertion("nosuchfunc", "a", "b");
        when(conditionResolver.resolve(any()))
                .thenThrow(new BratException("No ConditionResolverRule recognizes the function 'nosuchfunc'"));

        // when
        var result = assertionResolver.resolve(assertion, runtimeData);

        // then
        assertThat(result)
                .singleElement()
                .satisfies(r -> assertThat(r.passed()).isFalse())
                // as written, since a BratException's message was phrased for an author: the type is
                // named only for an exception we did not plan, which this is not
                .satisfies(r -> assertThat(r.message())
                        .contains("No ConditionResolverRule recognizes the function 'nosuchfunc'")
                        .doesNotContain("BratException"));
    }

    @Test
    void resolve_keepsResolvingTheChainAfterOneConditionFailsToResolve() {
        // given
        var chain = List.of(new ChainedCondition("!equals", "c"), new ChainedCondition("contains", "d"));
        var assertion = new Assertion("equals", "a", "b", chain);
        when(conditionResolver.resolve(any()))
                .thenReturn(true)
                .thenThrow(new BratException("boom"))
                .thenReturn(true);

        // when
        var result = assertionResolver.resolve(assertion, runtimeData);

        // then - one result per condition still, with only the throwing one failed
        assertThat(result).hasSize(3);
        assertThat(result.get(0).passed()).isTrue();
        assertThat(result.get(1).passed()).isFalse();
        assertThat(result.get(2).passed()).isTrue();
    }

    @Test
    void resolve_namesTheExceptionTypeWhenAResolverThrowsSomethingElse() {
        // given - a defect in core or a plugin, not an authoring mistake
        var assertion = new Assertion("equals", "a", "b");
        when(conditionResolver.resolve(any())).thenThrow(new IllegalStateException("no rule state"));

        // when
        var result = assertionResolver.resolve(assertion, runtimeData);

        // then
        assertThat(result)
                .singleElement()
                .satisfies(r -> assertThat(r.passed()).isFalse())
                .satisfies(r -> assertThat(r.message()).contains("IllegalStateException"));
    }

    @Test
    void resolve_doesNotCatchAnError() {
        // given
        var assertion = new Assertion("equals", "a", "b");
        when(conditionResolver.resolve(any())).thenThrow(new StackOverflowError());

        // when / then
        assertThatThrownBy(() -> assertionResolver.resolve(assertion, runtimeData))
                .isInstanceOf(StackOverflowError.class);
    }

    @Test
    void resolve_interpolatesTheSubjectOnceForTheWholeChain() {
        // given
        var chain = List.of(new ChainedCondition("startsWith", "Jo"), new ChainedCondition("contains", "hn"));
        var assertion = new Assertion("isNotNull", "${__uuid()}", null, chain);
        when(conditionResolver.resolve(any())).thenReturn(true);

        // when
        var result = assertionResolver.resolve(assertion, runtimeData);

        // then - every link tests the same interpolated a
        assertThat(result).hasSize(3);
        assertThat(result)
                .extracting(r -> r.condition().getA())
                .containsOnly(result.getFirst().condition().getA());
    }

    @Test
    void resolve_carriesEachLinksOwnOutcomesOntoItsCondition() {
        // given
        var chain = List.of(new ChainedCondition("contains", "${vars.x}"));
        var assertion = new Assertion("isNotNull", "${vars.a}", null, chain);
        when(conditionResolver.resolve(any())).thenReturn(true);

        // when
        var result = assertionResolver.resolve(assertion, runtimeData);

        // then - a from the assertion, b from the link, on one condition
        assertThat(result.getLast().condition().getOutcomes()).containsKeys("a", "b");
    }

    @Test
    void allAssertionsAreInterpolated() {
        // given
        var chain = List.of(new ChainedCondition("!equals", "c", "chainedFail"));
        var assertion = new Assertion("equals", "a", "b", chain, "primaryFail");
        when(conditionResolver.resolve(any())).thenReturn(true);
        when(interpolation.outcome(any(), any())).thenReturn(new InterpolationOutcome("interpolated", "interpolated"));

        // when
        var result = assertionResolver.resolve(assertion, runtimeData);

        // then
        assertThat(result)
                .hasSize(2)
                .allMatch(AssertionResult::passed)
                .allMatch(assertionResult -> assertionResult.condition().getA().equals("interpolated"))
                .allMatch(assertionResult -> assertionResult.condition().getB().equals("interpolated"));
    }

    @Test
    void resolve_throwsForAnAlreadyInterpolatedAssertion() {
        // given - a wiring error, not an authoring one, so it must not become a failed result
        var interpolated = new AssertionInterpolator(new ChainedConditionInterpolator())
                .interpolated(new Assertion("isEqualTo", "a", "b"), interpolation, runtimeData);

        // when / then
        assertThatThrownBy(() -> assertionResolver.resolve(interpolated, runtimeData))
                .isInstanceOf(BratException.class);
    }

    @Test
    void resolve_buildsLinkConditionsWhenTheAssertionHasNoSubject() {
        // given - a null a records no "a" outcome, so the link condition carries only its own
        var chain = List.of(new ChainedCondition("isNotNull", "b"));
        var assertion = new Assertion("isNull", null, null, chain, null);
        when(conditionResolver.resolve(any())).thenReturn(true);

        // when
        var result = assertionResolver.resolve(assertion, runtimeData);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.getLast().condition().getA()).isNull();
        assertThat(result.getLast().condition().getOutcomes()).doesNotContainKey("a");
    }
}
