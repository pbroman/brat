package dev.pbroman.brat.core.resolver.assertion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.api.resolver.AssertionResolver;
import dev.pbroman.brat.core.api.resolver.ConditionResolver;
import dev.pbroman.brat.core.data.Assertion;
import dev.pbroman.brat.core.data.ChainedAssertion;
import dev.pbroman.brat.core.data.ConditionInterpolation;
import dev.pbroman.brat.core.data.result.AssertionResult;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;

class DefaultAssertionResolverTest {

    Interpolation interpolation = Mockito.mock(Interpolation.class);
    ConditionResolver conditionResolver = Mockito.mock(ConditionResolver.class);
    RuntimeData runtimeData = Mockito.mock(RuntimeData.class);

    AssertionResolver assertionResolver =
            new DefaultAssertionResolver(interpolation, conditionResolver, new ConditionInterpolation());

    @BeforeEach
    void setUp() {
        // This is just to avoid a NPE
        when(interpolation.outcome(any(), any())).thenReturn(new InterpolationOutcome("something", "something"));
    }

    @Test
    void happyPath() {
        // given
        var chain = List.of(new ChainedAssertion("!equals", "c"));
        var assertion = new Assertion("a", "equals", "b", chain);
        when(conditionResolver.resolve(any())).thenReturn(true);

        // when
        var result = assertionResolver.resolve(assertion, runtimeData);

        // then
        assertThat(result).hasSize(2)
                .allMatch(AssertionResult::passed);
    }

    @Test
    void allAssertionsFail() {
        // given
        var chain = List.of(new ChainedAssertion("!equals", "c", "chainedFail"));
        var assertion = new Assertion("equals", "a",  "b", chain, "primaryFail");
        when(conditionResolver.resolve(any())).thenReturn(false);

        // when
        var result = assertionResolver.resolve(assertion, runtimeData);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.getFirst().message()).isEqualTo("primaryFail");
        assertThat(result.getLast().message()).isEqualTo("chainedFail");
    }

    @Test
    void addsFailByBratException() {
        // given
        var chain = List.of(new ChainedAssertion("!equals", "c", "chainedFail"));
        var assertion = new Assertion("equals", "a",  "b", chain, "primaryFail");
        when(conditionResolver.resolve(any())).thenReturn(true);
        when(interpolation.outcome(any(), any())).thenThrow(new BratException("interpolation failed"));

        // when
        var result = assertionResolver.resolve(assertion, runtimeData);

        // then
        assertThat(result).hasSize(2)
                .allMatch(assertionResult -> assertionResult.message().startsWith("Error interpolating"));
    }

    @Test
    void allAssertionsAreInterpolated() {
        // given
        var chain = List.of(new ChainedAssertion("!equals", "c", "chainedFail"));
        var assertion = new Assertion("equals", "a",  "b", chain, "primaryFail");
        when(conditionResolver.resolve(any())).thenReturn(true);
        when(interpolation.outcome(any(), any())).thenReturn(new InterpolationOutcome("interpolated", "interpolated"));

        // when
        var result = assertionResolver.resolve(assertion, runtimeData);

        // then
        assertThat(result).hasSize(2)
                .allMatch(AssertionResult::passed)
                .allMatch(assertionResult -> assertionResult.condition().getA().equals("interpolated"))
                .allMatch(assertionResult -> assertionResult.condition().getB().equals("interpolated"));
    }

}