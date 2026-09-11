package dev.pbroman.brat.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.api.listener.RunEvent;
import dev.pbroman.brat.integration.support.EndToEndTestBase;
import dev.pbroman.brat.integration.support.TestRunControl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The event stream a run emits, and stopping a run through it.
 *
 * <p>Cancellation is the half that needs a real server: that a stopped run leaves the second request
 * unsent is only observable in the state the server was not asked to change.
 */
class RunEventsTest extends EndToEndTestBase {

    @Test
    void run_emitsTheEventsInOrderAroundEveryRequest() {
        // given
        var events = new ArrayList<RunEvent>();

        // when
        BRAT.run(suite("suites/two-creates.yaml"), environment(Map.of()), List.of(events::add), new TestRunControl());

        // then
        assertThat(events)
                .extracting(event -> event.getClass().getSimpleName())
                .containsExactly(
                        "RunStarted",
                        "RequestStarted",
                        "RequestFinished",
                        "RequestStarted",
                        "RequestFinished",
                        "RunFinished");
    }

    @Test
    void run_reportsTheSameResultToTheListenerAsToTheCaller() {
        // given
        var events = new ArrayList<RunEvent>();

        // when
        var returned = BRAT.run(
                suite("suites/two-creates.yaml"), environment(Map.of()), List.of(events::add), new TestRunControl());

        // then — two ways to observe one run, never two answers about it
        var finished = (RunEvent.RunFinished) events.getLast();
        assertThat(finished.result()).isSameAs(returned);
    }

    @Test
    void run_stopsBetweenRequestsWhenAListenerCancels() {
        // given — a listener that pulls the trigger as soon as the first request is done
        var control = new TestRunControl();
        var events = new ArrayList<RunEvent>();

        // when
        var result = BRAT.run(
                suite("suites/two-creates.yaml"),
                environment(Map.of()),
                List.of(events::add, event -> {
                    if (event instanceof RunEvent.RequestFinished) {
                        control.cancel();
                    }
                }),
                control);

        // then — what completed is reported, and the run says it was stopped
        assertThat(result.cancelled()).isTrue();
        assertThat(result.failed()).isTrue();
        assertThat(result.requestResults()).hasSize(1);

        // and — the second request never reached the wire, which only the server can say
        assertThat(crud.get("/all").size()).isEqualTo(1);

        // and — the terminal event still arrived
        assertThat(events.getLast()).isInstanceOf(RunEvent.RunFinished.class);
    }
}
