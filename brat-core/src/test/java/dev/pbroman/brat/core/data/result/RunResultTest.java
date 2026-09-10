package dev.pbroman.brat.core.data.result;

import java.util.List;

import dev.pbroman.brat.core.data.Condition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RunResultTest {

    private static RequestResult requestResult(boolean failing) {
        var assertionResult = new AssertionResult(
                new Condition("isEqualTo", "a", "b"), "", !failing, dev.pbroman.brat.core.data.AssertionSeverity.FAIL);
        return new RequestResult(
                new RequestCoordinates("/s/r", null, "r", 1),
                null,
                new RequestStatus.Completed(java.util.Map.of(), 1, 5),
                10,
                new ResponseActionsResult(List.of(assertionResult), List.of()));
    }

    @Test
    void failed_isFalseForAnEmptyUncancelledRun() {
        // when
        var result = new RunResult(List.of(), 0, false);

        // then — an empty run failed nothing
        assertThat(result.failed()).isFalse();
    }

    @Test
    void failed_isFalseWhenEveryRequestPassed() {
        // when
        var result = new RunResult(List.of(requestResult(false), requestResult(false)), 20, false);

        // then
        assertThat(result.failed()).isFalse();
    }

    @Test
    void failed_isTrueWhenAnyRequestFailed() {
        // when
        var result = new RunResult(List.of(requestResult(false), requestResult(true)), 20, false);

        // then
        assertThat(result.failed()).isTrue();
    }

    @Test
    void failed_isTrueWhenCancelledEvenThoughEveryRequestPassed() {
        // when — a run stopped part-way established nothing about what it did not reach
        var result = new RunResult(List.of(requestResult(false)), 10, true);

        // then
        assertThat(result.failed()).isTrue();
    }
}
