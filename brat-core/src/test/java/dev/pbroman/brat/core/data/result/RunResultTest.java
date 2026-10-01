package dev.pbroman.brat.core.data.result;

import java.util.List;

import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.data.Phase;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RunResultTest {

    private static RequestResult requestResult(boolean failing) {
        var assertionResult = new AssertionResult(
                new Condition("isEqualTo", "a", "b"), "", !failing, dev.pbroman.brat.core.data.AssertionSeverity.FAIL);
        return new RequestResult(
                new RequestCoordinates("/s/r", null, "r", Phase.MAIN, 1),
                null,
                null,
                new RequestStatus.Completed(java.util.Map.of(), 1, 5),
                10,
                0,
                new ResponseActionsResult(List.of(assertionResult), List.of()));
    }

    @Test
    void failed_isFalseForAnEmptyUncancelledRun() {
        // when
        var result = new RunResult(List.of(), List.of(), 0, false, null);

        // then — an empty run failed nothing
        assertThat(result.failed()).isFalse();
    }

    @Test
    void failed_isFalseWhenEveryRequestPassed() {
        // when
        var result = new RunResult(List.of(requestResult(false), requestResult(false)), List.of(), 20, false, null);

        // then
        assertThat(result.failed()).isFalse();
    }

    @Test
    void failed_isTrueWhenAnyRequestFailed() {
        // when
        var result = new RunResult(List.of(requestResult(false), requestResult(true)), List.of(), 20, false, null);

        // then
        assertThat(result.failed()).isTrue();
    }

    @Test
    void failed_isTrueWhenCancelledEvenThoughEveryRequestPassed() {
        // when — a run stopped part-way established nothing about what it did not reach
        var result = new RunResult(List.of(requestResult(false)), List.of(), 10, true, null);

        // then
        assertThat(result.failed()).isTrue();
    }

    @Test
    void failed_isTrueWhenASuiteWasAbortedEvenThoughNoRequestRan() {
        // when - an entire subtree that never ran must not read as green
        var result = new RunResult(List.of(), List.of(new SuiteError("s/admin", "setVars failed")), 10, false, null);

        // then
        assertThat(result.failed()).isTrue();
    }

    @Test
    void constructor_rejectsNullSuiteErrors() {
        // when / then
        assertThatThrownBy(() -> new RunResult(List.of(), null, 0, false, null)).isInstanceOf(BratException.class);
    }

    @Test
    void constructor_copiesTheSuiteErrorsAndHandsOutAnUnmodifiableList() {
        // given
        var errors = new java.util.ArrayList<SuiteError>();
        errors.add(new SuiteError("s/a", "boom"));

        // when
        var result = new RunResult(List.of(), errors, 0, false, null);
        errors.add(new SuiteError("s/b", "later"));

        // then
        assertThat(result.suiteErrors()).containsExactly(new SuiteError("s/a", "boom"));
        assertThatThrownBy(() -> result.suiteErrors().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void failed_isTrueForARunThatEndedOnAStructuralFailure() {
        // given - nothing failed before it, yet the run did not establish anything
        var result = new RunResult(List.of(requestResult(false)), List.of(), 10, false, "no handler named 'x'");

        // when / then
        assertThat(result.failed()).isTrue();
    }
}
