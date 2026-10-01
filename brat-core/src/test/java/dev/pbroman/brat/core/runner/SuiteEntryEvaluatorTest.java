package dev.pbroman.brat.core.runner;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.data.TestSuite;
import dev.pbroman.brat.core.data.result.SuiteStatus;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SuiteEntryEvaluatorTest {

    private Interpolation interpolation;
    private ConditionEvaluator conditionEvaluator;
    private RuntimeData runtimeData;
    private SuiteEntryEvaluator underTest;

    @BeforeEach
    void setUp() {
        interpolation = mock(Interpolation.class);
        conditionEvaluator = mock(ConditionEvaluator.class);
        runtimeData = new RuntimeData(Map.of(), Map.of());
        runtimeData.setCurrentPath("root/admin");
        // Every value resolves to itself upper-cased, unless a test says otherwise.
        when(interpolation.interpolate(anyString(), any()))
                .thenAnswer(call -> ((String) call.getArgument(0)).toUpperCase());
        underTest = new SuiteEntryEvaluator(interpolation, conditionEvaluator);
    }

    // ---------- neither declared ----------

    @Test
    void enter_walksIntoASuiteDeclaringNeither() {
        // when
        var verdict = underTest.enter(suite(Map.of(), null), runtimeData);

        // then
        assertThat(verdict).isEmpty();
    }

    // ---------- setVars ----------

    @Test
    void enter_setsEverySetVarsEntry() {
        // when
        var verdict = underTest.enter(suite(ordered("a", "x", "b", "y"), null), runtimeData);

        // then
        assertThat(verdict).isEmpty();
        assertThat(runtimeData.getVars()).containsEntry("a", "X").containsEntry("b", "Y");
    }

    @Test
    void enter_stillEvaluatesAndTombstonesLaterEntriesAfterOneFails() {
        // given - the second and third fail; stopping at the second would leave the third unmarked
        when(interpolation.interpolate(eq("bad2"), any())).thenThrow(new BratException("no bad2"));
        when(interpolation.interpolate(eq("bad3"), any())).thenThrow(new BratException("no bad3"));

        // when
        underTest.enter(suite(ordered("a", "ok", "b", "bad2", "c", "bad3", "d", "fine"), null), runtimeData);

        // then - every key ends up either set or tombstoned
        assertThat(runtimeData.getVars()).containsEntry("a", "OK").containsEntry("d", "FINE");
        assertThat(runtimeData.getTombstone("b")).isNotNull();
        assertThat(runtimeData.getTombstone("c")).isNotNull();
    }

    @Test
    void enter_abortsNamingEveryFailedKeyAndItsCause() {
        // given
        when(interpolation.interpolate(eq("bad2"), any())).thenThrow(new BratException("no bad2"));
        when(interpolation.interpolate(eq("bad3"), any())).thenThrow(new BratException("no bad3"));

        // when
        var verdict = underTest.enter(suite(ordered("a", "ok", "b", "bad2", "c", "bad3"), null), runtimeData);

        // then
        assertThat(verdict)
                .get()
                .asInstanceOf(type(SuiteStatus.Aborted.class))
                .extracting(SuiteStatus.Aborted::reason)
                .asString()
                .contains("'b'", "no bad2", "'c'", "no bad3")
                .doesNotContain("'a'");
    }

    @Test
    void enter_stampsATombstoneWithTheCurrentPath() {
        // given
        when(interpolation.interpolate(eq("bad"), any())).thenThrow(new BratException("nope"));

        // when
        underTest.enter(suite(ordered("token", "bad"), null), runtimeData);

        // then - a sibling reading it later must be told which suite failed to set it
        assertThat(runtimeData.getTombstone("token").path()).isEqualTo("root/admin");
        assertThat(runtimeData.getTombstone("token").message()).contains("nope");
    }

    @Test
    void enter_doesNotEvaluateTheSkipConditionWhenAnEntryFailed() {
        // given
        when(interpolation.interpolate(eq("bad"), any())).thenThrow(new BratException("nope"));

        // when
        underTest.enter(suite(ordered("token", "bad"), skipCondition()), runtimeData);

        // then
        verify(conditionEvaluator, never()).evaluate(any(), any());
    }

    // ---------- skipCondition ----------

    @Test
    void enter_masksASecretInTheSkipReason() {
        // given
        var interpolated = new Condition(
                "isNotEmpty",
                "s3cr3t",
                null,
                null,
                Map.of("a", new InterpolationOutcome("s3cr3t", "${secrets.flag} → ***", true)));
        when(conditionEvaluator.evaluate(any(), any()))
                .thenReturn(new ConditionEvaluator.Evaluation(interpolated, true));

        // when
        var verdict = underTest.enter(suite(Map.of(), skipCondition()), runtimeData);

        // then
        assertThat(verdict)
                .get()
                .asInstanceOf(type(SuiteStatus.Skipped.class))
                .extracting(SuiteStatus.Skipped::reason)
                .asString()
                .contains("***")
                .doesNotContain("s3cr3t");
    }

    @Test
    void enter_skipsNamingTheInterpolatedConditionWhenItHolds() {
        // given
        var interpolated = new Condition("isTrue", "true");
        when(conditionEvaluator.evaluate(any(), any()))
                .thenReturn(new ConditionEvaluator.Evaluation(interpolated, true));

        // when
        var verdict = underTest.enter(suite(Map.of(), skipCondition()), runtimeData);

        // then
        assertThat(verdict)
                .get()
                .asInstanceOf(type(SuiteStatus.Skipped.class))
                .extracting(SuiteStatus.Skipped::reason)
                .asString()
                .contains(interpolated.toString());
    }

    @Test
    void enter_walksIntoTheSuiteWhenTheSkipConditionDoesNotHold() {
        // given
        when(conditionEvaluator.evaluate(any(), any()))
                .thenReturn(new ConditionEvaluator.Evaluation(new Condition("isTrue", "false"), false));

        // when
        var verdict = underTest.enter(suite(Map.of(), skipCondition()), runtimeData);

        // then
        assertThat(verdict).isEmpty();
    }

    @Test
    void enter_abortsWhenTheSkipConditionCannotBeEvaluated() {
        // given - a guard that cannot be read must not be guessed either way
        when(conditionEvaluator.evaluate(any(), any())).thenThrow(new BratException("no func 'isTru'"));

        // when
        var verdict = underTest.enter(suite(Map.of(), skipCondition()), runtimeData);

        // then
        assertThat(verdict)
                .get()
                .asInstanceOf(type(SuiteStatus.Aborted.class))
                .extracting(SuiteStatus.Aborted::reason)
                .asString()
                .startsWith("The skip condition failed")
                .contains("isTru");
    }

    @Test
    void enter_evaluatesSetVarsBeforeTheSkipCondition() {
        // given - a skip condition may read a var the same suite computes
        when(conditionEvaluator.evaluate(any(), any()))
                .thenReturn(new ConditionEvaluator.Evaluation(new Condition("isTrue", "false"), false));

        // when
        underTest.enter(suite(ordered("flag", "x"), skipCondition()), runtimeData);

        // then
        var order = inOrder(interpolation, conditionEvaluator);
        order.verify(interpolation).interpolate(eq("x"), any());
        order.verify(conditionEvaluator).evaluate(any(), any());
    }

    // ---------- structural ----------

    @Test
    void enter_throwsForANullSuite() {
        // when / then
        assertThatThrownBy(() -> underTest.enter(null, runtimeData)).isInstanceOf(BratException.class);
    }

    @Test
    void enter_throwsForNullRuntimeData() {
        // when / then
        assertThatThrownBy(() -> underTest.enter(suite(Map.of(), null), null)).isInstanceOf(BratException.class);
    }

    // ---------- helpers ----------

    private static Condition skipCondition() {
        return new Condition("isTrue", "${vars.skip}");
    }

    private static Map<String, String> ordered(String... keysAndValues) {
        var map = new LinkedHashMap<String, String>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    private static TestSuite suite(Map<String, String> setVars, Condition skipCondition) {
        return new TestSuite("admin", null, null, setVars, null, null, skipCondition, null, List.of(), null);
    }
}
