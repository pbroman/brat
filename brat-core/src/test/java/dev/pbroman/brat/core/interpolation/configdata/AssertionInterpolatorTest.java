package dev.pbroman.brat.core.interpolation.configdata;

import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.data.Assertion;
import dev.pbroman.brat.core.data.AssertionSeverity;
import dev.pbroman.brat.core.data.ChainedCondition;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssertionInterpolatorTest {

    private final AssertionInterpolator interpolator = new AssertionInterpolator(new ChainedConditionInterpolator());

    private Interpolation interpolation;
    private RuntimeData runtimeData;

    @BeforeEach
    void setUp() {
        interpolation = mock(Interpolation.class);
        runtimeData = mock(RuntimeData.class);
        when(interpolation.outcome(anyString(), any()))
                .thenAnswer(i -> new InterpolationOutcome("i:" + i.getArgument(0), "r:" + i.getArgument(0)));
    }

    @Test
    void interpolated_interpolatesTheMessage() {
        // given
        var assertion = new Assertion("isEqualTo", "${response.statusCode}", "200", "expected ${vars.want}");

        // when
        var result = interpolator.interpolated(assertion, interpolation, runtimeData);

        // then
        assertThat(result.getMessage()).isEqualTo("i:expected ${vars.want}");
    }

    @Test
    void interpolated_returnsAnAssertionNotAPlainCondition() {
        // given
        var chain = List.of(new ChainedCondition("contains", "x"));
        var assertion = new Assertion("isEqualTo", "a", "b", chain, "a message");
        assertion.setSeverity(AssertionSeverity.WARN);

        // when
        var result = interpolator.interpolated(assertion, interpolation, runtimeData);

        // then — the three fields an Assertion adds over a Condition all survive the copy
        assertThat(result).isInstanceOf(Assertion.class);
        assertThat(result.getChain()).hasSize(1);
        assertThat(result.getMessage()).isNotNull();
        assertThat(result.getSeverity()).isEqualTo(AssertionSeverity.WARN);
    }

    @Test
    void interpolated_interpolatesEveryChainLinkInDeclarationOrder() {
        // given
        var chain = List.of(new ChainedCondition("startsWith", "${vars.p}"), new ChainedCondition("contains", "hn"));
        var assertion = new Assertion("isNotNull", "${response.json.$.name}", null, chain);

        // when
        var result = interpolator.interpolated(assertion, interpolation, runtimeData);

        // then
        assertThat(result.getChain())
                .hasSize(2)
                .allMatch(ChainedCondition::isInterpolated)
                .extracting(ChainedCondition::getB)
                .containsExactly("i:${vars.p}", "i:hn");
    }

    @Test
    void interpolated_interpolatesTheSubjectExactlyOnce() {
        // given — two links; a is interpolated for the assertion and reused, never re-interpolated
        var chain = List.of(new ChainedCondition("startsWith", "Jo"), new ChainedCondition("contains", "hn"));
        var assertion = new Assertion("isNotNull", "${__uuid()}", null, chain);

        // when
        interpolator.interpolated(assertion, interpolation, runtimeData);

        // then
        verify(interpolation, times(1)).outcome(eq("${__uuid()}"), any());
    }

    @Test
    void interpolated_keysOutcomesByFieldAndArgNameWithoutTheChain() {
        // given
        var chain = List.of(new ChainedCondition("contains", "hn", "link message"));
        var assertion = new Assertion("isCloseTo", "a-value", "b-value", chain, "a message");
        assertion.setArgs(Map.of("offset", "0.01"));

        // when
        var result = interpolator.interpolated(assertion, interpolation, runtimeData);

        // then — the link's own outcomes stay on the link
        assertThat(result.getOutcomes()).containsOnlyKeys("a", "b", "args.offset", "message");
        assertThat(result.getChain().getFirst().getOutcomes()).containsOnlyKeys("b", "message");
    }

    @Test
    void interpolated_copiesFuncAndSeverityThrough() {
        // given
        var assertion = new Assertion("startsWith", "a", "b");
        assertion.setSeverity(AssertionSeverity.WARN);

        // when
        var result = interpolator.interpolated(assertion, interpolation, runtimeData);

        // then
        assertThat(result.getFunc()).isEqualTo("startsWith");
        assertThat(result.getSeverity()).isEqualTo(AssertionSeverity.WARN);
    }

    @Test
    void interpolated_recordsNoOutcomeForAbsentOptionalFields() {
        // given — no b, no message, no args, no chain
        var assertion = new Assertion("isNotNull", "a-value");

        // when
        var result = interpolator.interpolated(assertion, interpolation, runtimeData);

        // then
        assertThat(result.getB()).isNull();
        assertThat(result.getMessage()).isNull();
        assertThat(result.getChain()).isEmpty();
        assertThat(result.getOutcomes()).containsOnlyKeys("a");
    }

    @Test
    void interpolated_throwsExceptionIfCopy() {
        // given
        var assertion = new Assertion("isEqualTo", "a", "b");
        var interpolated = interpolator.interpolated(assertion, interpolation, runtimeData);

        // then
        assertThatThrownBy(() -> interpolator.interpolated(interpolated, interpolation, runtimeData))
                .isInstanceOf(BratException.class);
    }

    @Test
    void interpolated_propagatesAFailureFromAChainLink() {
        // given
        var chain = List.of(new ChainedCondition("contains", "${vars.boom}"));
        var assertion = new Assertion("isNotNull", "a-value", null, chain);
        when(interpolation.outcome(eq("${vars.boom}"), any())).thenThrow(new BratException("nope"));

        // then — a chain is interpolated wholly or not at all
        assertThatThrownBy(() -> interpolator.interpolated(assertion, interpolation, runtimeData))
                .isInstanceOf(BratException.class);
    }
}
