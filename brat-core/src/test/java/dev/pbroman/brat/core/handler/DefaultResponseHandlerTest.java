package dev.pbroman.brat.core.handler;

import static org.apache.commons.lang3.BooleanUtils.TRUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.resolver.ConditionResolver;
import dev.pbroman.brat.core.data.Assertion;
import dev.pbroman.brat.core.data.ResponseActions;
import dev.pbroman.brat.core.data.result.ValidationType;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.ValidationException;
import dev.pbroman.brat.core.resolver.assertion.DefaultAssertionResolver;

class DefaultResponseHandlerTest {

    DefaultResponseHandler underTest;
    Interpolation interpolation;

    @BeforeEach
    void setUp() {
        interpolation = mock(Interpolation.class);
        var conditionResolver = mock(ConditionResolver.class);
        var assertionResolver = new DefaultAssertionResolver(interpolation, conditionResolver);
        underTest = new DefaultResponseHandler(interpolation, assertionResolver);
    }

    @Test
    void noAssertions_emptyResult() {
        // given
        var responseActions = new ResponseActions(List.of(), Map.of());

        // when
        var assertionResults = underTest.handleResponse(responseActions, mock(RuntimeData.class));

        // then
        assertThat(assertionResults).isEmpty();
    }

    @Test
    void oneAssertion_resultReturned() {
        // given
        var assertion = new Assertion(TRUE, true);
        var responseActions = new ResponseActions(List.of(assertion), Map.of());

        // when
        var assertionResults = underTest.handleResponse(responseActions, mock(RuntimeData.class));

        // then
        assertThat(assertionResults).hasSize(1);
    }

    @Test
    void setVars_extendsRuntimeData() throws ValidationException {
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
    void setVars_catchesValidationException() throws ValidationException {
        // given
        var responseActions = new ResponseActions(List.of(), Map.of("moo", "baa"));
        var runtimeData = new RuntimeData(Map.of(), Map.of());
        when(interpolation.interpolate(anyString(), eq(runtimeData)))
                .thenThrow(new ValidationException("bollocks", ValidationType.FAIL));

        // when
        underTest.handleResponse(responseActions, runtimeData);

        // then
        assertThat(runtimeData.getVars()).isEmpty();
        assertThat(runtimeData.getValidations()).isNotEmpty();
        assertThat(runtimeData.getValidations().getFirst().validationType()).isEqualTo(ValidationType.FAIL);
    }

}