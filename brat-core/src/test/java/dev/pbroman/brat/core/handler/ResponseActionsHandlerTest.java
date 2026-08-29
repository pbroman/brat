package dev.pbroman.brat.core.handler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.api.resolver.ConditionResolver;
import dev.pbroman.brat.core.data.Assertion;
import dev.pbroman.brat.core.data.ResponseActions;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.interpolation.configdata.AssertionInterpolator;
import dev.pbroman.brat.core.interpolation.configdata.ChainedConditionInterpolator;
import dev.pbroman.brat.core.resolver.assertion.AssertionChainResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.commons.lang3.BooleanUtils.TRUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResponseActionsHandlerTest {

    ResponseActionsHandler underTest;
    Interpolation interpolation;

    @BeforeEach
    void setUp() {
        interpolation = mock(Interpolation.class);
        // This is just to avoid a NPE
        when(interpolation.outcome(any(), any())).thenReturn(new InterpolationOutcome("something", "something"));
        var conditionResolver = mock(ConditionResolver.class);
        var assertionResolver = new AssertionChainResolver(
                interpolation, conditionResolver, new AssertionInterpolator(new ChainedConditionInterpolator()));
        underTest = new ResponseActionsHandler(interpolation, assertionResolver);
    }

    @Test
    void noAssertions_emptyResult() {
        // given
        var responseActions = new ResponseActions(List.of(), Map.of());

        // when
        var result = underTest.handleResponse(responseActions, mock(RuntimeData.class));

        // then
        assertThat(result.assertionResults()).isEmpty();
        assertThat(result.captureFailures()).isEmpty();
    }

    @Test
    void oneAssertion_resultReturned() {
        // given
        var assertion = new Assertion(TRUE, true);
        var responseActions = new ResponseActions(List.of(assertion), Map.of());

        // when
        var result = underTest.handleResponse(responseActions, mock(RuntimeData.class));

        // then
        assertThat(result.assertionResults()).hasSize(1);
    }

    @Test
    void setVars_extendsRuntimeData() {
        // given
        var responseActions = new ResponseActions(List.of(), Map.of("moo", "baa"));
        var runtimeData = new RuntimeData(Map.of(), Map.of());
        when(interpolation.interpolate(anyString(), eq(runtimeData)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        underTest.handleResponse(responseActions, runtimeData);

        // then
        assertThat(runtimeData.getVars()).isNotEmpty();
        assertThat(runtimeData.getVars().get("moo")).isEqualTo("baa");
    }

    @Test
    void setVars_recordsACaptureFailureInsteadOfPropagating() {
        // given
        var responseActions = new ResponseActions(List.of(), Map.of("moo", "${response.json.$.nope}"));
        var runtimeData = new RuntimeData(Map.of(), Map.of());
        when(interpolation.interpolate(anyString(), eq(runtimeData))).thenThrow(new BratException("bollocks"));

        // when - inside a request nothing escapes
        var result = underTest.handleResponse(responseActions, runtimeData);

        // then
        assertThat(result.captureFailures()).singleElement().satisfies(failure -> {
            assertThat(failure.name()).isEqualTo("moo");
            assertThat(failure.expression()).isEqualTo("${response.json.$.nope}");
            assertThat(failure.message()).contains("bollocks");
        });
    }

    @Test
    void setVars_failureIsNotAnAssertionResult() {
        // given
        var responseActions = new ResponseActions(List.of(), Map.of("moo", "baa"));
        var runtimeData = new RuntimeData(Map.of(), Map.of());
        when(interpolation.interpolate(anyString(), eq(runtimeData))).thenThrow(new BratException("bollocks"));

        // when
        var result = underTest.handleResponse(responseActions, runtimeData);

        // then - synthesizing one would print a condition the author never wrote
        assertThat(result.assertionResults()).isEmpty();
    }

    @Test
    void setVars_leavesATombstoneOnTheFailedVariable() {
        // given
        var responseActions = new ResponseActions(List.of(), Map.of("moo", "baa"));
        var runtimeData = new RuntimeData(Map.of(), Map.of());
        runtimeData.setCurrentPath("happy path/create an order");
        when(interpolation.interpolate(anyString(), eq(runtimeData))).thenThrow(new BratException("bollocks"));

        // when
        underTest.handleResponse(responseActions, runtimeData);

        // then
        assertThat(runtimeData.getVars()).doesNotContainKey("moo");
        assertThat(runtimeData.getTombstone("moo")).isNotNull();
        assertThat(runtimeData.getTombstone("moo").path()).isEqualTo("happy path/create an order");
    }

    @Test
    void setVars_evaluatesTheRemainingCapturesAfterOneFails() {
        // given - the first entry throws, the second resolves
        var setVars = new LinkedHashMap<String, String>();
        setVars.put("broken", "${response.json.$.nope}");
        setVars.put("fine", "baa");
        var responseActions = new ResponseActions(List.of(), setVars);
        var runtimeData = new RuntimeData(Map.of(), Map.of());
        when(interpolation.interpolate(eq("${response.json.$.nope}"), eq(runtimeData)))
                .thenThrow(new BratException("bollocks"));
        when(interpolation.interpolate(eq("baa"), eq(runtimeData))).thenReturn("baa");

        // when
        var result = underTest.handleResponse(responseActions, runtimeData);

        // then - one broken entry must not hide the rest
        assertThat(result.captureFailures()).hasSize(1);
        assertThat(runtimeData.getVars()).containsEntry("fine", "baa");
    }

    @Test
    void setVars_catchesMoreThanBratException() {
        // given - interpolation and condition rules are plugin extension points, so core cannot
        // enumerate what arrives
        var responseActions = new ResponseActions(List.of(), Map.of("moo", "baa"));
        var runtimeData = new RuntimeData(Map.of(), Map.of());
        when(interpolation.interpolate(anyString(), eq(runtimeData)))
                .thenThrow(new IllegalStateException("not a BratException"));

        // when
        var result = underTest.handleResponse(responseActions, runtimeData);

        // then
        assertThat(result.captureFailures())
                .singleElement()
                .satisfies(failure -> assertThat(failure.message()).contains("IllegalStateException"));
    }
}
