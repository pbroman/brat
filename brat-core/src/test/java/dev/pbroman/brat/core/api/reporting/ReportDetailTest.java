package dev.pbroman.brat.core.api.reporting;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.data.AssertionSeverity;
import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.data.Phase;
import dev.pbroman.brat.core.data.result.AssertionResult;
import dev.pbroman.brat.core.data.result.RequestCoordinates;
import dev.pbroman.brat.core.data.result.RequestResult;
import dev.pbroman.brat.core.data.result.RequestStatus;
import dev.pbroman.brat.core.data.result.ResponseActionsResult;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportDetailTest {

    private static final RequestCoordinates COORDINATES =
            new RequestCoordinates("suite/request", null, "request", Phase.MAIN, 1);

    // --- fixtures ---

    private static RequestStatus.Completed completed() {
        return new RequestStatus.Completed(Map.of("statusCode", 200), 1, 10);
    }

    private static AssertionResult assertion(boolean passed, AssertionSeverity severity) {
        return new AssertionResult(new Condition("isEqualTo", "a", "b"), null, passed, severity);
    }

    private static RequestResult result(RequestStatus status, AssertionResult... assertions) {
        return new RequestResult(
                COORDINATES, null, null, status, 10, 0, new ResponseActionsResult(List.of(assertions), List.of()));
    }

    private static RequestResult passed() {
        return result(completed(), assertion(true, AssertionSeverity.FAIL));
    }

    private static RequestResult failed() {
        return result(completed(), assertion(false, AssertionSeverity.FAIL));
    }

    private static RequestResult errored() {
        return result(new RequestStatus.Errored("connection refused"));
    }

    private static RequestResult skipped() {
        return result(new RequestStatus.Skipped("Skipped due to condition"));
    }

    private static RequestResult gaveUp() {
        return result(new RequestStatus.GaveUp(completed(), "never COMPLETE"));
    }

    private static ReportDetail detail(Show response, Show request) {
        return new ReportDetail(response, request, Show.FAILURES, false);
    }

    // --- constructor ---

    @Test
    void constructor_throwsForANullResponse() {
        // when / then
        assertThatThrownBy(() -> new ReportDetail(null, Show.ALL, Show.ALL, false))
                .isInstanceOf(BratException.class);
    }

    @Test
    void constructor_throwsForANullRequest() {
        // when / then
        assertThatThrownBy(() -> new ReportDetail(Show.ALL, null, Show.ALL, false))
                .isInstanceOf(BratException.class);
    }

    @Test
    void constructor_throwsForNullAssertions() {
        // when / then
        assertThatThrownBy(() -> new ReportDetail(Show.ALL, Show.ALL, null, false))
                .isInstanceOf(BratException.class);
    }

    @Test
    void constructor_throwsForAssertionsNone() {
        // when / then
        assertThatThrownBy(() -> new ReportDetail(Show.ALL, Show.ALL, Show.NONE, false))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("none");
    }

    // --- of ---

    @Test
    void of_appliesEveryDefaultToEmptyArgs() {
        // when
        var detail = ReportDetail.of(Map.of());

        // then
        assertThat(detail).isEqualTo(new ReportDetail(Show.FAILURES, Show.FAILURES, Show.FAILURES, false));
    }

    @Test
    void of_readsEveryKey() {
        // given
        var args = Map.of("response", "all", "request", "none", "assertions", "all", "secrets", "show");

        // when
        var detail = ReportDetail.of(args);

        // then
        assertThat(detail).isEqualTo(new ReportDetail(Show.ALL, Show.NONE, Show.ALL, true));
    }

    @Test
    void of_readsSecretsMaskAsNotShowing() {
        // when
        var detail = ReportDetail.of(Map.of("secrets", "mask"));

        // then
        assertThat(detail.showSecrets()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"failures", "all", "none"})
    void of_acceptsEveryScopeForResponseAndRequest(String value) {
        // when
        var detail = ReportDetail.of(Map.of("response", value, "request", value));

        // then
        assertThat(detail.response().name()).isEqualToIgnoringCase(value);
        assertThat(detail.request().name()).isEqualToIgnoringCase(value);
    }

    @Test
    void of_leavesKeysItDoesNotReadAlone() {
        // given - a reporter's own argument
        var args = Map.of("file", "report.xml", "response", "all");

        // when
        var detail = ReportDetail.of(args);

        // then
        assertThat(detail.response()).isEqualTo(Show.ALL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"response", "request"})
    void of_rejectsAnUnknownScope(String key) {
        // when / then
        assertThatThrownBy(() -> ReportDetail.of(Map.of(key, "some")))
                .isInstanceOf(BratException.class)
                .hasMessageContaining(key)
                .hasMessageContaining("some")
                .hasMessageContaining("failures")
                .hasMessageContaining("all")
                .hasMessageContaining("none");
    }

    @Test
    void of_rejectsAScopeInAnotherCase() {
        // when / then
        assertThatThrownBy(() -> ReportDetail.of(Map.of("response", "ALL"))).isInstanceOf(BratException.class);
    }

    @Test
    void of_rejectsAssertionsNone() {
        // when / then
        assertThatThrownBy(() -> ReportDetail.of(Map.of("assertions", "none")))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("assertions")
                .hasMessageContaining("failures")
                .hasMessageContaining("all");
    }

    @Test
    void of_rejectsAnUnknownSecretsValue() {
        // when / then
        assertThatThrownBy(() -> ReportDetail.of(Map.of("secrets", "yes")))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("secrets")
                .hasMessageContaining("yes")
                .hasMessageContaining("mask")
                .hasMessageContaining("show");
    }

    @Test
    void of_throwsForNullArgs() {
        // when / then
        assertThatThrownBy(() -> ReportDetail.of(null)).isInstanceOf(BratException.class);
    }

    @Test
    void of_treatsANullValueAsAbsent() {
        // given
        var args = new HashMap<String, String>();
        args.put("response", null);

        // when
        var detail = ReportDetail.of(args);

        // then - an absent value is the default, like an absent key
        assertThat(detail.response()).isEqualTo(Show.FAILURES);
    }

    @Test
    void keys_holdsTheFourArgumentsInDocumentedOrder() {
        // when / then
        assertThat(ReportDetail.KEYS).containsExactly("response", "request", "assertions", "secrets");
    }

    // --- showsRequest ---

    @Test
    void showsRequest_failuresShowsAFailedRequest() {
        // when / then
        assertThat(detail(Show.NONE, Show.FAILURES).showsRequest(failed())).isTrue();
    }

    @Test
    void showsRequest_failuresShowsAnErroredRequest() {
        // when / then
        assertThat(detail(Show.NONE, Show.FAILURES).showsRequest(errored())).isTrue();
    }

    @Test
    void showsRequest_failuresShowsAGivenUpPoll() {
        // when / then
        assertThat(detail(Show.NONE, Show.FAILURES).showsRequest(gaveUp())).isTrue();
    }

    @Test
    void showsRequest_failuresHidesAPassedRequest() {
        // when / then
        assertThat(detail(Show.NONE, Show.FAILURES).showsRequest(passed())).isFalse();
    }

    @Test
    void showsRequest_failuresHidesARequestWhoseOnlyFailureIsAWarning() {
        // given
        var result = result(completed(), assertion(false, AssertionSeverity.WARN));

        // then - the request follows the request's verdict, not the assertion's
        assertThat(detail(Show.NONE, Show.FAILURES).showsRequest(result)).isFalse();
    }

    @Test
    void showsRequest_allShowsAPassedRequest() {
        // when / then
        assertThat(detail(Show.NONE, Show.ALL).showsRequest(passed())).isTrue();
    }

    @Test
    void showsRequest_neverShowsASkippedRequest() {
        // when / then
        assertThat(detail(Show.NONE, Show.ALL).showsRequest(skipped())).isFalse();
        assertThat(detail(Show.NONE, Show.FAILURES).showsRequest(skipped())).isFalse();
    }

    @Test
    void showsRequest_noneShowsNothing() {
        // when / then
        assertThat(detail(Show.ALL, Show.NONE).showsRequest(failed())).isFalse();
        assertThat(detail(Show.ALL, Show.NONE).showsRequest(errored())).isFalse();
    }

    @Test
    void showsRequest_throwsForANullResult() {
        // when / then
        assertThatThrownBy(() -> detail(Show.ALL, Show.ALL).showsRequest(null)).isInstanceOf(BratException.class);
    }

    // --- showsResponse ---

    @Test
    void showsResponse_failuresShowsAFailedRequestsResponse() {
        // when / then
        assertThat(detail(Show.FAILURES, Show.NONE).showsResponse(failed())).isTrue();
    }

    @Test
    void showsResponse_failuresShowsAGivenUpPollsLastResponse() {
        // when / then
        assertThat(detail(Show.FAILURES, Show.NONE).showsResponse(gaveUp())).isTrue();
    }

    @Test
    void showsResponse_failuresHidesAPassedRequestsResponse() {
        // when / then
        assertThat(detail(Show.FAILURES, Show.NONE).showsResponse(passed())).isFalse();
    }

    @Test
    void showsResponse_allShowsAPassedRequestsResponse() {
        // when / then
        assertThat(detail(Show.ALL, Show.NONE).showsResponse(passed())).isTrue();
    }

    @Test
    void showsResponse_neverShowsAnErroredRequest() {
        // when / then
        // an errored request carries no response, whatever the scope
        assertThat(detail(Show.ALL, Show.NONE).showsResponse(errored())).isFalse();
        assertThat(detail(Show.FAILURES, Show.NONE).showsResponse(errored())).isFalse();
    }

    @Test
    void showsResponse_neverShowsASkippedRequest() {
        // when / then
        assertThat(detail(Show.ALL, Show.NONE).showsResponse(skipped())).isFalse();
    }

    @Test
    void showsResponse_noneShowsNothing() {
        // when / then
        assertThat(detail(Show.NONE, Show.ALL).showsResponse(failed())).isFalse();
    }

    @Test
    void showsResponse_throwsForANullResult() {
        // when / then
        assertThatThrownBy(() -> detail(Show.ALL, Show.ALL).showsResponse(null)).isInstanceOf(BratException.class);
    }

    // --- showsAssertion ---

    @Test
    void showsAssertion_failuresShowsAFailedAssertion() {
        // given
        var detail = new ReportDetail(Show.NONE, Show.NONE, Show.FAILURES, false);

        // then
        assertThat(detail.showsAssertion(assertion(false, AssertionSeverity.FAIL)))
                .isTrue();
    }

    @Test
    void showsAssertion_failuresShowsAFailedWarning() {
        // given
        var detail = new ReportDetail(Show.NONE, Show.NONE, Show.FAILURES, false);

        // then
        assertThat(detail.showsAssertion(assertion(false, AssertionSeverity.WARN)))
                .isTrue();
    }

    @Test
    void showsAssertion_failuresHidesAPassedAssertion() {
        // given
        var detail = new ReportDetail(Show.NONE, Show.NONE, Show.FAILURES, false);

        // then
        assertThat(detail.showsAssertion(assertion(true, AssertionSeverity.FAIL)))
                .isFalse();
    }

    @Test
    void showsAssertion_allShowsAPassedAssertion() {
        // given
        var detail = new ReportDetail(Show.NONE, Show.NONE, Show.ALL, false);

        // then
        assertThat(detail.showsAssertion(assertion(true, AssertionSeverity.FAIL)))
                .isTrue();
    }

    @Test
    void showsAssertion_throwsForANullAssertion() {
        // given
        var detail = new ReportDetail(Show.NONE, Show.NONE, Show.ALL, false);

        // then
        assertThatThrownBy(() -> detail.showsAssertion(null)).isInstanceOf(BratException.class);
    }
}
