package dev.pbroman.brat.core.resolver.assertion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.resolver.AssertionResolver;
import dev.pbroman.brat.core.api.resolver.ConditionResolver;
import dev.pbroman.brat.core.data.Assertion;
import dev.pbroman.brat.core.data.ChainedAssertion;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.ValidationException;

class DefaultAssertionResolverTest {

    Interpolation interpolation = Mockito.mock(Interpolation.class);
    ConditionResolver conditionResolver = Mockito.mock(ConditionResolver.class);
    RuntimeData runtimeData = Mockito.mock(RuntimeData.class);

    AssertionResolver assertionResolver = new DefaultAssertionResolver(interpolation, conditionResolver);

    @BeforeEach
    void setUp() throws ValidationException {
        // This is just to avoid a NPE
        when(interpolation.interpolate(any(), any())).thenReturn("something");
    }

    @Test
    void happyPath() throws ValidationException {
        // given
        var chain = List.of(new ChainedAssertion("!equals", "c"));
        var assertion = new Assertion("a", "equals", "b", chain);
        when(conditionResolver.resolve(any())).thenReturn(true);

        // when
        var result = assertionResolver.resolve(assertion, runtimeData);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void primaryAssertionFail() throws ValidationException {
        // given
        var chain = List.of(new ChainedAssertion("!equals", "c"));
        var assertion = new Assertion("equals", "a",  "b", chain, "primaryFail");
        when(conditionResolver.resolve(any())).thenReturn(true);
        when(conditionResolver.resolve(eq(assertion))).thenReturn(false);

        // when
        var result = assertionResolver.resolve(assertion, runtimeData);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().message()).isEqualTo("primaryFail");
    }

    @Test
    void allAssertionsFail() throws ValidationException {
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

}