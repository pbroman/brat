package dev.pbroman.brat.junit;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Event;
import org.junit.platform.testkit.engine.EventType;
import org.opentest4j.AssertionFailedError;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/**
 * What JUnit reports for a suite's tests: each fixture under {@code suites/bridge/} run from a real
 * {@code @TestFactory} through JUnit's own engine, the events read back.
 */
class BratTestsRunTest {

    private static final String SECRET = "s3cr3t-token";

    private static TestServer server;

    @BeforeAll
    static void startServer() {
        server = new TestServer();
    }

    @AfterAll
    static void stopServer() {
        server.close();
        server = null;
    }

    @BeforeEach
    void forget() {
        server.clear();
    }

    private static BratTests fixture(String name) {
        return BratTests.suite("brat-junit/src/test/resources/suites/bridge/" + name + ".brat.yaml")
                .env("baseUrl", server.url())
                .param("secrets.token", SECRET);
    }

    /**
     * Whether a factory below is being run by this class through the testkit; run any other way — by an
     * IDE or a {@code -Dtest} pattern that reaches nested classes — it is disabled.
     *
     * @return whether the server the factories point at is up
     */
    static boolean underTestKit() {
        return server != null;
    }

    // --- the factories JUnit runs ---

    @EnabledIf("dev.pbroman.brat.junit.BratTestsRunTest#underTestKit")
    static class Outcomes {
        @TestFactory
        Stream<DynamicNode> outcomes() {
            return fixture("outcomes").tests();
        }
    }

    @EnabledIf("dev.pbroman.brat.junit.BratTestsRunTest#underTestKit")
    static class SuitesEndingEarly {
        @TestFactory
        Stream<DynamicNode> suitesEndingEarly() {
            return fixture("suites-ending-early").tests();
        }
    }

    @EnabledIf("dev.pbroman.brat.junit.BratTestsRunTest#underTestKit")
    static class RootAborted {
        @TestFactory
        Stream<DynamicNode> rootAborted() {
            return fixture("root-aborted").tests();
        }
    }

    @EnabledIf("dev.pbroman.brat.junit.BratTestsRunTest#underTestKit")
    static class RootSkipped {
        @TestFactory
        Stream<DynamicNode> rootSkipped() {
            return fixture("root-skipped").tests();
        }
    }

    @EnabledIf("dev.pbroman.brat.junit.BratTestsRunTest#underTestKit")
    static class MissingBody {
        @TestFactory
        Stream<DynamicNode> missingBody() {
            return fixture("missing-body").tests();
        }
    }

    @EnabledIf("dev.pbroman.brat.junit.BratTestsRunTest#underTestKit")
    static class MissingHandler {
        @TestFactory
        Stream<DynamicNode> missingHandler() {
            return fixture("missing-handler").tests();
        }
    }

    private static List<Event> run(Class<?> factory) {
        return EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(factory))
                .execute()
                .allEvents()
                .list();
    }

    /**
     * Executes every test of {@code tests} as Jupiter would, depth first, recording what each one writes
     * to standard output while it — and only it — executes.
     */
    private static Map<String, String> printedByEachTest(BratTests tests) {
        var printed = new LinkedHashMap<String, String>();
        try (var stream = tests.tests()) {
            executeAll(stream.iterator(), printed);
        }
        return printed;
    }

    private static void executeAll(Iterator<? extends DynamicNode> nodes, Map<String, String> printed) {
        while (nodes.hasNext()) {
            var node = nodes.next();
            if (node instanceof DynamicContainer container) {
                executeAll(container.getChildren().iterator(), printed);
                continue;
            }
            var test = (DynamicTest) node;
            var buffer = new ByteArrayOutputStream();
            var original = System.out;
            System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
            try {
                test.getExecutable().execute();
            } catch (Throwable verdict) {
                // a failed or skipped test: its verdict is not what is asserted here
            } finally {
                System.setOut(original);
            }
            printed.put(test.getDisplayName(), buffer.toString(StandardCharsets.UTF_8));
        }
    }

    /** As {@link #run}, with Jupiter's parallel execution on and every test concurrent by default. */
    private static List<Event> runInParallel(Class<?> factory) {
        return EngineTestKit.engine("junit-jupiter")
                .configurationParameter("junit.jupiter.execution.parallel.enabled", "true")
                .configurationParameter("junit.jupiter.execution.parallel.mode.default", "concurrent")
                .configurationParameter("junit.jupiter.execution.parallel.config.strategy", "fixed")
                .configurationParameter("junit.jupiter.execution.parallel.config.fixed.parallelism", "4")
                .selectors(selectClass(factory))
                .execute()
                .allEvents()
                .list();
    }

    // --- reading the events ---

    private static List<String> testsStarted(List<Event> events) {
        return events.stream()
                .filter(event -> event.getType() == EventType.STARTED)
                .filter(event -> event.getTestDescriptor().isTest())
                .map(event -> event.getTestDescriptor().getDisplayName())
                .toList();
    }

    private static TestExecutionResult finished(List<Event> events, String name) {
        return events.stream()
                .filter(event -> event.getType() == EventType.FINISHED)
                .filter(event -> event.getTestDescriptor().getDisplayName().equals(name))
                .findFirst()
                .flatMap(event -> event.getPayload(TestExecutionResult.class))
                .orElseThrow(() -> new AssertionError("no test finished named " + name));
    }

    private static String message(List<Event> events, String name) {
        return finished(events, name).getThrowable().map(Throwable::getMessage).orElse(null);
    }

    private static Optional<String> parentOf(List<Event> events, String name) {
        return events.stream()
                .map(Event::getTestDescriptor)
                .filter(descriptor -> descriptor.getDisplayName().equals(name))
                .findFirst()
                .flatMap(TestDescriptor::getParent)
                .map(TestDescriptor::getDisplayName);
    }

    // --- what the tests are ---

    @Test
    void tests_handsJUnitOneTestPerRequestInRunOrderNamedAsTheConsoleNamesThem() {
        // when
        var events = run(Outcomes.class);

        // then
        assertThat(testsStarted(events))
                .containsExactly(
                        "warm up [setup]",
                        "passes",
                        "fails an assertion",
                        "warns only",
                        "fails a capture",
                        "errors",
                        "gives up",
                        "is skipped",
                        "lists orders",
                        "clean up [teardown]");
    }

    @Test
    void tests_giveEveryTestItsOwnRequestsVerdictUnderParallelExecution() {
        // when
        var events = runInParallel(Outcomes.class);

        // then
        assertThat(finished(events, "passes").getStatus()).isEqualTo(TestExecutionResult.Status.SUCCESSFUL);
        assertThat(finished(events, "warns only").getStatus()).isEqualTo(TestExecutionResult.Status.SUCCESSFUL);
        assertThat(finished(events, "fails an assertion").getStatus()).isEqualTo(TestExecutionResult.Status.FAILED);
        assertThat(message(events, "fails an assertion")).startsWith("fails an assertion");
        assertThat(message(events, "fails a capture")).startsWith("fails a capture");
        assertThat(message(events, "errors")).startsWith("errors — ");
        assertThat(finished(events, "is skipped").getStatus()).isEqualTo(TestExecutionResult.Status.ABORTED);
        assertThat(finished(events, "lists orders").getStatus()).isEqualTo(TestExecutionResult.Status.SUCCESSFUL);
    }

    @Test
    void tests_printEachRequestOnItsOwnTestAsBratRunWould() {
        // when
        var printed = printedByEachTest(fixture("outcomes"));

        // then
        assertThat(printed.get("passes")).startsWith("PASS   passes (");
        assertThat(printed.get("fails an assertion")).startsWith("FAIL   fails an assertion (");
        assertThat(printed.get("errors")).startsWith("ERROR  errors (");
        assertThat(printed.get("is skipped")).startsWith("SKIP   is skipped — ");
        assertThat(printed.get("warm up [setup]")).startsWith("PASS   warm up [setup] (");
        assertThat(printed.get("lists orders")).startsWith("PASS   lists orders (");
        assertThat(printed.get("passes").lines()).hasSize(1);
        assertThat(printed.get("fails an assertion").lines()).hasSize(1);
    }

    @Test
    void tests_printTheEndOfAnAbortedOrSkippedSuiteOnItsTest() {
        // when
        var printed = printedByEachTest(fixture("suites-ending-early"));

        // then
        assertThat(printed.get("checkout: aborted")).startsWith("ABORTED checkout — ");
        assertThat(printed.get("later: skipped")).startsWith("SKIPPED later — ");
    }

    @Test
    void tests_putsASubSuitesTestsInAContainerNamedAfterIt() {
        // when
        var events = run(Outcomes.class);

        // then
        assertThat(parentOf(events, "lists orders")).contains("orders");
        assertThat(parentOf(events, "passes")).contains("outcomes()");
    }

    @Test
    void tests_passARequestThatPassedAFailedWarnAssertionIncluded() {
        // when
        var events = run(Outcomes.class);

        // then
        assertThat(finished(events, "passes").getStatus()).isEqualTo(TestExecutionResult.Status.SUCCESSFUL);
        assertThat(finished(events, "warns only").getStatus()).isEqualTo(TestExecutionResult.Status.SUCCESSFUL);
        assertThat(finished(events, "warm up [setup]").getStatus()).isEqualTo(TestExecutionResult.Status.SUCCESSFUL);
    }

    @Test
    void tests_failAFailedAssertionWithTheRequestAndWhatTheConsolePrintsBeneathIt() {
        // when
        var events = run(Outcomes.class);

        // then
        var result = finished(events, "fails an assertion");
        assertThat(result.getStatus()).isEqualTo(TestExecutionResult.Status.FAILED);
        assertThat(result.getThrowable()).containsInstanceOf(AssertionFailedError.class);
        var lines = message(events, "fails an assertion").lines().toList();
        assertThat(lines.getFirst()).startsWith("fails an assertion");
        assertThat(lines.subList(1, lines.size()))
                .allSatisfy(line -> assertThat(line).startsWith("  "));
        assertThat(lines).anySatisfy(line -> assertThat(line).isEqualTo("  FAIL  isEqualTo — must be ok"));
        assertThat(lines).anySatisfy(line -> assertThat(line).isEqualTo("  response: 500"));
    }

    @Test
    void tests_maskSecretsInAFailure() {
        // when
        var events = run(Outcomes.class);

        // then
        assertThat(message(events, "fails an assertion")).contains("request:").doesNotContain(SECRET);
    }

    @Test
    void tests_failAFailedCapture() {
        // when
        var events = run(Outcomes.class);

        // then
        assertThat(finished(events, "fails a capture").getStatus()).isEqualTo(TestExecutionResult.Status.FAILED);
        assertThat(message(events, "fails a capture")).contains("CAPTURE missing");
    }

    @Test
    void tests_failAnErroredRequestNamingItsReasonOnTheFirstLine() {
        // when
        var events = run(Outcomes.class);

        // then
        assertThat(finished(events, "errors").getStatus()).isEqualTo(TestExecutionResult.Status.FAILED);
        assertThat(message(events, "errors").lines().findFirst().orElseThrow())
                .startsWith("errors — ")
                .contains("noSuchKey");
    }

    @Test
    void tests_failARequestThatGaveUpPollingNamingItsReasonOnTheFirstLine() {
        // when
        var events = run(Outcomes.class);

        // then
        assertThat(finished(events, "gives up").getStatus()).isEqualTo(TestExecutionResult.Status.FAILED);
        assertThat(message(events, "gives up").lines().findFirst().orElseThrow())
                .startsWith("gives up — ")
                .contains("never answered 201");
    }

    @Test
    void tests_abortASkippedRequestWithTheSkipsReason() {
        // when
        var events = run(Outcomes.class);

        // then
        var result = finished(events, "is skipped");
        assertThat(result.getStatus()).isEqualTo(TestExecutionResult.Status.ABORTED);
        assertThat(message(events, "is skipped")).isNotBlank();
        assertThat(server.received()).doesNotContain("/ok?skipped");
    }

    @Test
    void tests_endAnAbortedSuiteWithAFailingTestAndHoldASkippedSuitesOneSkippedTest() {
        // when
        var events = run(SuitesEndingEarly.class);

        // then
        assertThat(testsStarted(events))
                .containsExactly("open a basket [setup]", "checkout: aborted", "later: skipped", "after both");
        assertThat(parentOf(events, "checkout: aborted")).contains("checkout");
        assertThat(finished(events, "checkout: aborted").getStatus()).isEqualTo(TestExecutionResult.Status.FAILED);
        assertThat(message(events, "checkout: aborted")).isNotBlank();
        assertThat(parentOf(events, "later: skipped")).contains("later");
        assertThat(finished(events, "later: skipped").getStatus()).isEqualTo(TestExecutionResult.Status.ABORTED);
        assertThat(server.received()).doesNotContain("/ok?pay", "/ok?not-yet");
    }

    @Test
    void tests_endTheStreamWithAFailingTestWhenTheSuiteItselfIsAborted() {
        // when
        var events = run(RootAborted.class);

        // then
        assertThat(testsStarted(events)).containsExactly("log in [setup]", "root aborted: aborted");
        assertThat(parentOf(events, "root aborted: aborted")).contains("rootAborted()");
        assertThat(finished(events, "root aborted: aborted").getStatus()).isEqualTo(TestExecutionResult.Status.FAILED);
    }

    @Test
    void tests_holdOneSkippedTestWhenTheSuiteItselfIsSkipped() {
        // when
        var events = run(RootSkipped.class);

        // then
        assertThat(testsStarted(events)).containsExactly("root skipped: skipped");
        assertThat(finished(events, "root skipped: skipped").getStatus()).isEqualTo(TestExecutionResult.Status.ABORTED);
        assertThat(server.received()).isEmpty();
    }

    @Test
    void tests_endTheStreamWithARunFailedTestWhenTheRunFailsBeforeItsFirstEvent() {
        // when
        var events = run(MissingBody.class);

        // then
        assertThat(testsStarted(events)).containsExactly("missing body: run failed");
        assertThat(finished(events, "missing body: run failed").getStatus())
                .isEqualTo(TestExecutionResult.Status.FAILED);
        assertThat(message(events, "missing body: run failed")).contains("not-there.json");
        assertThat(server.received()).isEmpty();
    }

    @Test
    void tests_endTheStreamWithARunFailedTestWhenTheRunEndsInAnError() {
        // when
        var events = run(MissingHandler.class);

        // then
        assertThat(testsStarted(events)).endsWith("missing handler: run failed");
        assertThat(finished(events, "missing handler: run failed").getStatus())
                .isEqualTo(TestExecutionResult.Status.FAILED);
        assertThat(message(events, "missing handler: run failed")).contains("no-such-handler");
    }
}
