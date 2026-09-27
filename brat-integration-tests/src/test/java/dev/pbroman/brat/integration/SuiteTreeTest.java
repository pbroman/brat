package dev.pbroman.brat.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.api.listener.RunEvent;
import dev.pbroman.brat.core.data.result.RequestResult;
import dev.pbroman.brat.core.data.result.RequestStatus;
import dev.pbroman.brat.core.data.result.RunResult;
import dev.pbroman.brat.core.data.result.SuiteError;
import dev.pbroman.brat.core.data.result.SuiteStatus;
import dev.pbroman.brat.integration.support.EndToEndTestBase;
import dev.pbroman.brat.integration.support.TestRunControl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

/**
 * A suite tree walked against the real server: the order at every level, what a suite declares for
 * the requests beneath it, and what happens to a subtree that cannot run.
 *
 * <p>Each fixture pins one property. The scenarios create their own data through BRAT, because
 * "each subtree establishes what it needs, independently of its siblings" is the claim.
 */
class SuiteTreeTest extends EndToEndTestBase {

    private final List<RunEvent> events = new ArrayList<>();

    private RunResult runWithEvents(String resource, Map<String, Object> env) {
        return BRAT.run(suite(resource), environment(env), List.of(events::add), new TestRunControl());
    }

    private static List<String> pathsOf(RunResult result) {
        return result.requestResults().stream()
                .map(request -> request.coordinates().path())
                .toList();
    }

    private List<SuiteStatus> statusOf(String path) {
        return events.stream()
                .filter(RunEvent.SuiteExited.class::isInstance)
                .map(RunEvent.SuiteExited.class::cast)
                .filter(exited -> exited.path().equals(path))
                .map(RunEvent.SuiteExited::status)
                .toList();
    }

    // ---------- a green tree ----------

    @Test
    void run_walksATreeInTheDecidedOrderAndPasses() {
        // given
        var suite = suite("suites/tree/order.yaml");

        // when
        var result = run(suite, Map.of("tag", "t1"));

        // then - every assertion at every depth ran and passed
        assertPassed(result, suite);

        // and - setup first, then the level's own requests, then each scenario, then teardown
        assertThat(pathsOf(result))
                .containsExactly(
                        "order/reset",
                        "order/check it is empty",
                        "order/alice/create alice",
                        "order/alice/read alice",
                        "order/alice/delete alice",
                        "order/bob/create bob",
                        "order/bob/read bob",
                        "order/bob/delete bob",
                        "order/check it is empty again");

        // and - the root's waitAfter was resolved and carried
        assertThat(result.requestResults().getFirst().waitAfterMs()).isEqualTo(50L);
    }

    @Test
    void run_bracketsEverySuiteWithItsEvents() {
        // when
        runWithEvents("suites/tree/order.yaml", Map.of("tag", "t2"));

        // then - three suites entered and exited, each completed
        assertThat(events).filteredOn(RunEvent.SuiteEntered.class::isInstance).hasSize(3);
        assertThat(statusOf("order")).singleElement().isInstanceOf(SuiteStatus.Completed.class);
        assertThat(statusOf("order/alice")).singleElement().isInstanceOf(SuiteStatus.Completed.class);
        assertThat(statusOf("order/bob")).singleElement().isInstanceOf(SuiteStatus.Completed.class);
    }

    // ---------- a failed setup ----------

    @Test
    void run_abortsOnlyTheScenarioWhoseSetupFailed() {
        // when
        var result = runWithEvents("suites/tree/setup-abort.yaml", Map.of());

        // then - the broken scenario's setup ran, its main request and subSuite did not, its teardown did
        assertThat(pathsOf(result))
                .containsExactly(
                        "setup abort/alice/create alice",
                        "setup abort/alice/list users",
                        "setup abort/broken/create a user without a name",
                        "setup abort/broken/clean up anyway",
                        "setup abort/carol/create carol",
                        "setup abort/carol/list users");

        // and - it is reported as aborted, naming the setup request, in both carriers
        assertThat(result.suiteErrors()).singleElement().satisfies(error -> {
            assertThat(error.path()).isEqualTo("setup abort/broken");
            assertThat(error.message()).contains("create a user without a name");
        });
        assertThat(statusOf("setup abort/broken")).singleElement().isInstanceOf(SuiteStatus.Aborted.class);
        assertThat(events)
                .filteredOn(RunEvent.SuiteEntered.class::isInstance)
                .map(event -> ((RunEvent.SuiteEntered) event).path())
                .doesNotContain("setup abort/broken/never entered");

        // and - the siblings are untouched, and the parent completed
        assertThat(result.requestResults())
                .filteredOn(request -> !request.coordinates().path().startsWith("setup abort/broken/"))
                .allSatisfy(request -> assertThat(request.failed()).isFalse());
        assertThat(statusOf("setup abort")).singleElement().isInstanceOf(SuiteStatus.Completed.class);
        assertThat(result.failed()).isTrue();
    }

    // ---------- a suite's own entry ----------

    @Test
    void run_abortsASuiteWhoseSetVarsFailsWithoutRunningItsTeardown() {
        // when - no adminToken is supplied
        var result = runWithEvents("suites/tree/entry.yaml", Map.of("tenant", "none"));

        // then - nothing under admin ran, its teardown included, and the run says why
        assertThat(pathsOf(result)).noneMatch(path -> path.startsWith("entry/admin/"));
        assertThat(result.suiteErrors()).singleElement().satisfies(error -> {
            assertThat(error.path()).isEqualTo("entry/admin");
            assertThat(error.message()).contains("token", "adminToken");
        });
        assertThat(statusOf("entry/admin")).singleElement().isInstanceOf(SuiteStatus.Aborted.class);
    }

    @Test
    void run_failsASiblingsReadOfTheVarNamingWhereItFailed() {
        // when
        var result = run("suites/tree/entry.yaml", Map.of("tenant", "none"));

        // then - the sibling's request errors instead of sending an empty token
        assertThat(result.requestResults())
                .filteredOn(request -> request.coordinates().path().equals("entry/reader/read with the admin token"))
                .singleElement()
                .extracting(RequestResult::status)
                .asInstanceOf(type(RequestStatus.Errored.class))
                .extracting(RequestStatus.Errored::message)
                .asString()
                .contains("token", "entry/admin");
    }

    @Test
    void run_skipsASuiteWhoseSkipConditionHolds() {
        // when
        var result = runWithEvents("suites/tree/entry.yaml", Map.of("tenant", "none"));

        // then - reported as skipped, nothing beneath it ran, and skipping is not an error
        assertThat(statusOf("entry/tenant only")).singleElement().isInstanceOf(SuiteStatus.Skipped.class);
        assertThat(pathsOf(result)).noneMatch(path -> path.startsWith("entry/tenant only/"));
        assertThat(result.suiteErrors()).extracting(SuiteError::path).doesNotContain("entry/tenant only");
    }

    // ---------- inheritance ----------

    @Test
    void run_inheritsASuitesTimeoutTwoLevelsDownAndLetsASubtreeOptOut() {
        // when - the root allows 200 ms; the server answers after 800
        var result = run("suites/tree/timeout.yaml", Map.of("timeout", "200", "delayMs", "800"));

        // then
        assertThat(result.requestResults())
                .extracting(
                        request -> request.coordinates().path(),
                        request -> request.status().getClass())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "timeout/outer/inherits/too slow", RequestStatus.Errored.class),
                        org.assertj.core.groups.Tuple.tuple(
                                "timeout/opted out/slow but allowed", RequestStatus.Completed.class));
    }
}
