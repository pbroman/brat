package dev.pbroman.brat.integration;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Event;
import org.junit.platform.testkit.engine.EventType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/**
 * What JUnit reports for {@code SuitesAsTestsTest}: the app started by {@code @SpringBootTest}, each
 * request of each suite a test of its own, with the outcome its result earns.
 */
class JUnitBridgeTest {

    private static List<Event> events;

    private static synchronized List<Event> events() {
        if (events == null) {
            events = EngineTestKit.engine("junit-jupiter")
                    .selectors(selectClass("dev.pbroman.brat.integration.app.SuitesAsTestsTest"))
                    .execute()
                    .allEvents()
                    .list();
        }
        return events;
    }

    private static List<String> started(String kind) {
        return events().stream()
                .filter(event -> event.getType() == EventType.STARTED)
                .filter(event -> kind.equals("test")
                        ? event.getTestDescriptor().isTest()
                        : event.getTestDescriptor().isContainer())
                .map(event -> event.getTestDescriptor().getDisplayName())
                .toList();
    }

    private static TestExecutionResult.Status statusOf(String name) {
        return events().stream()
                .filter(event -> event.getType() == EventType.FINISHED)
                .filter(event -> event.getTestDescriptor().getDisplayName().equals(name))
                .findFirst()
                .flatMap(event -> event.getPayload(TestExecutionResult.class))
                .map(TestExecutionResult::getStatus)
                .orElseThrow(() -> new AssertionError("no test finished named " + name));
    }

    @Test
    void suiteRunAsTests_reportsEveryRequestOfTheTreeAsAPassingTestInRunOrder() {
        // when
        var tests = started("test");

        // then
        assertThat(tests)
                .containsSubsequence(
                        "reset [setup]",
                        "check it is empty",
                        "create alice [setup]",
                        "read alice",
                        "check it is empty again [teardown]");
        assertThat(started("container")).contains("order()", "alice", "bob");
        for (var name :
                List.of("reset [setup]", "check it is empty", "read alice", "check it is empty again [teardown]")) {
            assertThat(statusOf(name)).as(name).isEqualTo(TestExecutionResult.Status.SUCCESSFUL);
        }
    }

    @Test
    void suiteRunAsTests_skipsTheLoginForAHandedOverTokenAndPassesWithIt() {
        // when
        var tests = started("test");

        // then
        assertThat(tests).containsSubsequence("get a token [setup]", "who am I");
        assertThat(statusOf("get a token [setup]")).isEqualTo(TestExecutionResult.Status.ABORTED);
        assertThat(statusOf("who am I")).isEqualTo(TestExecutionResult.Status.SUCCESSFUL);
    }

    @Test
    void suiteRunAsTests_failsNothing() {
        // when
        var failed = events().stream()
                .filter(event -> event.getType() == EventType.FINISHED)
                .filter(event -> event.getPayload(TestExecutionResult.class)
                        .map(result -> result.getStatus() == TestExecutionResult.Status.FAILED)
                        .orElse(false))
                .map(event -> event.getTestDescriptor().getDisplayName())
                .toList();

        // then
        assertThat(failed).isEmpty();
    }
}
