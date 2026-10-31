package dev.pbroman.brat.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.api.listener.RunControl;
import dev.pbroman.brat.core.api.listener.RunEvent;
import dev.pbroman.brat.integration.support.EndToEndTestBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The event stream a run emits, and stopping a run through it.
 *
 * <p>Stopping is the half that needs a real server: that a stopped run leaves the second request unsent,
 * and that a graceful stop still sends the teardown, is only observable in the server's state.
 */
class RunEventsTest extends EndToEndTestBase {

    @Test
    void run_emitsTheEventsInOrderAroundEveryRequest() {
        // given
        var events = new ArrayList<RunEvent>();

        // when
        BRAT.run(
                suite("suites/two-creates.brat.yaml"),
                environment(Map.of()),
                List.of(events::add),
                RunControl.create());

        // then
        assertThat(events)
                .extracting(event -> event.getClass().getSimpleName())
                .containsExactly(
                        "RunStarted",
                        "SuiteEntered",
                        "RequestStarted",
                        "RequestFinished",
                        "RequestStarted",
                        "RequestFinished",
                        "SuiteExited",
                        "RunFinished");
    }

    @Test
    void run_reportsTheSameResultToTheListenerAsToTheCaller() {
        // given
        var events = new ArrayList<RunEvent>();

        // when
        var returned = BRAT.run(
                suite("suites/two-creates.brat.yaml"),
                environment(Map.of()),
                List.of(events::add),
                RunControl.create());

        // then — two ways to observe one run, never two answers about it
        var finished = (RunEvent.RunFinished) events.getLast();
        assertThat(finished.result()).isSameAs(returned);
    }

    @Test
    void run_stopsBetweenRequestsWhenAListenerCancels() {
        // given — a listener that pulls the trigger as soon as the first request is done
        var control = RunControl.create();
        var events = new ArrayList<RunEvent>();

        // when
        var result = BRAT.run(
                suite("suites/two-creates.brat.yaml"),
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

    @Test
    void run_runsTheTeardownWhenStoppedGracefully() {
        // given — a stop pressed once the setup request has created a user
        var control = RunControl.create();

        // when
        var result = BRAT.run(
                suite("suites/graceful-stop.brat.yaml"),
                environment(Map.of()),
                List.of(event -> {
                    if (event instanceof RunEvent.RequestFinished) {
                        control.stop();
                    }
                }),
                control);

        // then — the main request was skipped, the teardown ran, and the run says it was stopped
        assertThat(result.cancelled()).isTrue();
        assertThat(result.requestResults())
                .extracting(requestResult -> requestResult.coordinates().name())
                .containsExactly("create the first user", "delete the first user");

        // and — the user it created is gone again, which only the server can say
        assertThat(crud.get("/all").size()).isZero();
    }

    @Test
    void run_skipsTheTeardownWhenCancelled() {
        // given — the same suite, cancelled rather than stopped
        var control = RunControl.create();

        // when
        var result = BRAT.run(
                suite("suites/graceful-stop.brat.yaml"),
                environment(Map.of()),
                List.of(event -> {
                    if (event instanceof RunEvent.RequestFinished) {
                        control.cancel();
                    }
                }),
                control);

        // then — nothing after the setup request reached the server, so its user is still there
        assertThat(result.cancelled()).isTrue();
        assertThat(result.requestResults()).hasSize(1);
        assertThat(crud.get("/all").size()).isEqualTo(1);
    }
}
