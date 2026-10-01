package dev.pbroman.brat.core.data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.interpolation.configdata.AssertionInterpolator;
import dev.pbroman.brat.core.interpolation.configdata.ChainedConditionInterpolator;
import dev.pbroman.brat.core.interpolation.configdata.ConditionInterpolator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConditionToStringTest {

    private static final String SECRET = "s3cr3t";

    private final ConditionInterpolator conditionInterpolator = new ConditionInterpolator();
    private final RuntimeData runtimeData = mock(RuntimeData.class);
    private Interpolation interpolation;

    @BeforeEach
    void setUp() {
        // ${secrets.…} resolves to SECRET and is flagged as one; any other ${…} resolves to
        // "resolved"; plain text resolves to itself. Copies are made by the real interpolators, so
        // the outcome keys are the ones production records.
        interpolation = mock(Interpolation.class);
        when(interpolation.outcome(anyString(), any())).thenAnswer(call -> {
            String input = call.getArgument(0);
            if (input.contains("${secrets.")) {
                return new InterpolationOutcome(SECRET, input + " → ***", true);
            }
            if (input.contains("${")) {
                return new InterpolationOutcome("resolved", input + " → resolved");
            }
            return new InterpolationOutcome(input, input);
        });
    }

    private Condition interpolated(Condition authored) {
        return conditionInterpolator.interpolated(authored, interpolation, runtimeData);
    }

    @Test
    void toString_showsAnAuthoredConditionAsWritten() {
        // given
        var authored = new Condition("isEqualTo", "${secrets.token}", "abc");

        // when
        var rendered = authored.toString();

        // then
        assertThat(rendered).isEqualTo("${secrets.token} isEqualTo abc");
    }

    @Test
    void toString_omitsANullB() {
        // given
        var authored = new Condition("isNull", "${vars.x}");

        // when
        var rendered = authored.toString();

        // then
        assertThat(rendered).isEqualTo("${vars.x} isNull");
    }

    @Test
    void toString_masksASecretScalarOperandOfAnInterpolatedCopy() {
        // given
        var copy = interpolated(new Condition("isEqualTo", "${secrets.token}", "abc"));

        // when
        var rendered = copy.toString();

        // then
        assertThat(rendered).isEqualTo("*** isEqualTo abc").doesNotContain(SECRET);
    }

    @Test
    void toString_showsANonSecretOperandOfAnInterpolatedCopyByItsValue() {
        // given
        var copy = interpolated(new Condition("isEqualTo", "${vars.name}", "abc"));

        // when
        var rendered = copy.toString();

        // then - the value, not the reportingString
        assertThat(rendered).isEqualTo("resolved isEqualTo abc");
    }

    @Test
    void toString_omitsANullBOnAnInterpolatedCopy() {
        // given
        var copy = interpolated(new Condition("isEmpty", "${secrets.token}"));

        // when
        var rendered = copy.toString();

        // then
        assertThat(rendered).isEqualTo("*** isEmpty");
    }

    @Test
    void toString_masksOnlyTheSecretLeavesOfAStructuredOperand() {
        // given
        var b = new LinkedHashMap<String, Object>();
        b.put("name", "${secrets.user}");
        b.put("role", "admin");
        var copy = interpolated(new Condition("isEqualTo", "${vars.user}", b));

        // when
        var rendered = copy.toString();

        // then - the shape survives, only the secret leaf is masked
        assertThat(rendered)
                .isEqualTo("resolved isEqualTo {name=***, role=admin}")
                .doesNotContain(SECRET);
    }

    @Test
    void toString_masksSecretElementsOfASequenceAtAnyDepth() {
        // given
        var b = List.of("plain", Map.of("token", "${secrets.token}"), List.of("${secrets.other}"));
        var copy = interpolated(new Condition("isEqualTo", "x", b));

        // when
        var rendered = copy.toString();

        // then
        assertThat(rendered)
                .isEqualTo("x isEqualTo [plain, {token=***}, [***]]")
                .doesNotContain(SECRET);
    }

    @Test
    void toString_showsANullLeafAsNull() {
        // given
        var b = new LinkedHashMap<String, Object>();
        b.put("name", null);
        var copy = interpolated(new Condition("isEqualTo", "x", b));

        // when
        var rendered = copy.toString();

        // then
        assertThat(rendered).isEqualTo("x isEqualTo {name=null}");
    }

    @Test
    void toString_masksALeafWithNoRecordedOutcome() {
        // given - an interpolated copy whose outcomes say nothing about a; nothing says it is safe
        var copy = new Condition("isEqualTo", SECRET, "abc", null, Map.of("b", new InterpolationOutcome("abc", "abc")));

        // when
        var rendered = copy.toString();

        // then
        assertThat(rendered).isEqualTo("*** isEqualTo abc");
    }

    @Test
    void toString_masksAnInterpolatedAssertion() {
        // given - Assertion inherits the rendering; its own fields are not part of it
        var interpolator = new AssertionInterpolator(new ChainedConditionInterpolator());
        var copy = interpolator.interpolated(
                new Assertion("isEqualTo", "${secrets.token}", "abc", "token must match"), interpolation, runtimeData);

        // when
        var rendered = copy.toString();

        // then
        assertThat(rendered).isEqualTo("*** isEqualTo abc");
    }

    @Test
    void toString_neverShowsArgs() {
        // given
        var copy = interpolated(new Condition("isCloseTo", "1.0", "1.1", Map.of("offset", "${secrets.offset}")));

        // when
        var rendered = copy.toString();

        // then
        assertThat(rendered).isEqualTo("1.0 isCloseTo 1.1");
    }

    @Test
    void toString_masksAWholeStructureOneSecretTokenResolvedTo() {
        // given - one outcome keyed "a" covers the whole map; there are no leaf outcomes to walk
        var resolved = Map.of("user", SECRET);
        var copy = new Condition(
                "isNotNull", resolved, null, null, Map.of("a", new InterpolationOutcome(resolved, "masked", true)));

        // when
        var rendered = copy.toString();

        // then
        assertThat(rendered).isEqualTo("*** isNotNull");
    }

    @Test
    void toString_showsAWholeStructureANonSecretTokenResolvedTo() {
        // given
        var resolved = Map.of("user", "bob");
        var copy = new Condition(
                "isNotNull", resolved, null, null, Map.of("a", new InterpolationOutcome(resolved, "reported")));

        // when
        var rendered = copy.toString();

        // then
        assertThat(rendered).isEqualTo("{user=bob} isNotNull");
    }
}
