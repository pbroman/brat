package dev.pbroman.brat.core.data.result;

import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.data.AssertionSeverity;
import dev.pbroman.brat.core.data.Condition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequestResultTest {

    private static final RequestCoordinates COORDINATES =
            new RequestCoordinates("suite/request", "req-1", "request", 1);

    @Test
    void failed_isFalseForACompletedRequestWithPassingAssertions() {
        // given
        var result = result(completed(), List.of(assertionResult(true, AssertionSeverity.FAIL)), List.of());

        // then
        assertThat(result.failed()).isFalse();
    }

    @Test
    void failed_isTrueForAFailedFailSeverityAssertion() {
        // given
        var result = result(completed(), List.of(assertionResult(false, AssertionSeverity.FAIL)), List.of());

        // then
        assertThat(result.failed()).isTrue();
    }

    @Test
    void failed_isFalseForAFailedWarnSeverityAssertion() {
        // given — a WARN failure is recorded and does not fail the request
        var result = result(completed(), List.of(assertionResult(false, AssertionSeverity.WARN)), List.of());

        // then
        assertThat(result.failed()).isFalse();
    }

    @Test
    void failed_isTrueWhenOneOfSeveralAssertionsFailed() {
        // given
        var result = result(
                completed(),
                List.of(assertionResult(true, AssertionSeverity.FAIL), assertionResult(false, AssertionSeverity.FAIL)),
                List.of());

        // then
        assertThat(result.failed()).isTrue();
    }

    @Test
    void failed_isTrueForACaptureFailure() {
        // given — a capture has no severity, so any failure counts
        var result = result(
                completed(),
                List.of(assertionResult(true, AssertionSeverity.FAIL)),
                List.of(new CaptureFailure("orderId", "${response.json.$.id}", "no body")));

        // then
        assertThat(result.failed()).isTrue();
    }

    @Test
    void failed_isTrueForAnErroredRequest() {
        // given
        var result = new RequestResult(
                COORDINATES, null, new RequestStatus.Errored("connection refused"), 12, ResponseActionsResult.NONE);

        // then
        assertThat(result.failed()).isTrue();
    }

    @Test
    void failed_isTrueForARequestThatGaveUpPolling() {
        // given — responses arrived and none ever matched, which is a failure, not a pass
        var lastAttempt = new RequestStatus.Completed(Map.of("statusCode", 202), 3, 11);
        var result = new RequestResult(
                COORDINATES,
                null,
                new RequestStatus.GaveUp(lastAttempt, "still processing after 3 attempts"),
                4000,
                ResponseActionsResult.NONE);

        // then
        assertThat(result.failed()).isTrue();
    }

    @Test
    void failed_isFalseForASkippedRequest() {
        // given — a skipped request did not run, so it did not fail
        var result = new RequestResult(
                COORDINATES, null, new RequestStatus.Skipped("skipCondition held"), 0, ResponseActionsResult.NONE);

        // then
        assertThat(result.failed()).isFalse();
    }

    @Test
    void constructor_defaultsResponseActionsResultToNone() {
        // when
        var result = new RequestResult(COORDINATES, null, completed(), 5, null);

        // then
        assertThat(result.responseActionsResult()).isEqualTo(ResponseActionsResult.NONE);
        assertThat(result.failed()).isFalse();
    }

    @Test
    void responseActionsResult_defaultsBothListsToEmpty() {
        // when - the record documents both components as never null
        var result = new ResponseActionsResult(null, null);

        // then
        assertThat(result.assertionResults()).isEmpty();
        assertThat(result.captureFailures()).isEmpty();
    }

    @Test
    void completed_copiesAndDefaultsResponseVars() {
        // given
        var responseVars = new java.util.HashMap<String, Object>();
        responseVars.put("statusCode", "200");

        // when
        var status = new RequestStatus.Completed(responseVars, 1, 42);
        responseVars.put("added", "later");

        // then — the result holds a snapshot; the runtime namespace moves on
        assertThat(status.responseVars()).containsOnlyKeys("statusCode");
        assertThat(new RequestStatus.Completed(null, 1, 42).responseVars()).isEmpty();
    }

    private static RequestStatus completed() {
        return new RequestStatus.Completed(Map.of("statusCode", "200"), 1, 42);
    }

    private static RequestResult result(
            RequestStatus status, List<AssertionResult> assertions, List<CaptureFailure> captures) {
        return new RequestResult(COORDINATES, null, status, 100, new ResponseActionsResult(assertions, captures));
    }

    private static AssertionResult assertionResult(boolean passed, AssertionSeverity severity) {
        return new AssertionResult(new Condition("isEqualTo", "a", "b"), "message", passed, severity);
    }
}
