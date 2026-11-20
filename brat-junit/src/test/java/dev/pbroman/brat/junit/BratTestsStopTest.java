package dev.pbroman.brat.junit;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * When a run starts and how it stops: the stream {@link BratTests#tests()} returns, driven by hand the
 * way Jupiter drives it — an element taken, its test executed, the next taken — so each step can be
 * held still while the server is asked what it saw.
 */
class BratTestsStopTest {

    private static TestServer server;

    @BeforeAll
    static void startServer() {
        server = new TestServer();
    }

    @AfterAll
    static void stopServer() {
        server.close();
    }

    @BeforeEach
    void forget() {
        server.clear();
    }

    private static BratTests fixture(String name) {
        return BratTests.suite("brat-junit/src/test/resources/suites/" + name).env("baseUrl", server.url());
    }

    private static DynamicTest test(Iterator<DynamicNode> nodes) {
        return (DynamicTest) nodes.next();
    }

    private static void runAll(Iterator<DynamicNode> nodes) throws Throwable {
        while (nodes.hasNext()) {
            test(nodes).getExecutable().execute();
        }
    }

    @Test
    void tests_startsNoRunUntilJUnitTakesTheFirstTest() throws Throwable {
        // given
        var stream = fixture("bridge/slow.brat.yaml").tests();

        // when
        Thread.sleep(200);

        // then
        assertThat(server.received()).isEmpty();
        try (stream) {
            runAll(stream.iterator());
        }
        assertThat(server.received()).contains("/ok?first");
    }

    @Test
    void tests_startsNoRunForAStreamClosedBeforeItIsRead() throws InterruptedException {
        // given
        var stream = fixture("bridge/slow.brat.yaml").tests();

        // when
        stream.close();
        Thread.sleep(200);

        // then
        assertThat(server.received()).isEmpty();
    }

    @Test
    void tests_runsTheSuiteAgainOnEachCall() throws Throwable {
        // given
        var tests = fixture("health");

        // when
        try (var first = tests.tests()) {
            runAll(first.iterator());
        }
        try (var second = tests.tests()) {
            runAll(second.iterator());
        }

        // then
        assertThat(server.received()).containsExactly("/health", "/health");
    }

    @Test
    void tests_stopsTheRunAndWaitsForItWhenTheStreamIsClosedEarly() throws Throwable {
        // given - the first test executed; the slow request under way, its test taken but not executed
        var stream = fixture("bridge/slow.brat.yaml").tests();
        var nodes = stream.iterator();
        test(nodes).getExecutable().execute();
        test(nodes);

        // when
        stream.close();

        // then - no new request started, the teardown ran, and both before close returned
        assertThat(server.received()).containsExactly("/ok?first", "/slow", "/ok?clean-up");
    }

    @Test
    void tests_stopsTheRunWhenATestIsInterruptedFailingThatTestAndHandingOverNoMore() throws Throwable {
        // given
        var stream = fixture("bridge/slow.brat.yaml").tests();
        var nodes = stream.iterator();
        test(nodes).getExecutable().execute();
        var slow = test(nodes);
        var thrown = new AtomicReference<Throwable>();
        var executing = new Thread(() -> {
            try {
                slow.getExecutable().execute();
            } catch (Throwable t) {
                thrown.set(t);
            }
        });

        // when
        executing.start();
        Thread.sleep(300);
        executing.interrupt();
        executing.join(TestServer.SLOW_MS * 4);

        // then
        assertThat(executing.isAlive()).isFalse();
        assertThat(thrown.get()).isNotNull();
        assertThat(thrown.get().getMessage()).containsIgnoringCase("interrupted");
        assertThat(nodes.hasNext()).isFalse();
        assertThat(server.received()).containsExactly("/ok?first", "/slow", "/ok?clean-up");
        stream.close();
    }

    @Test
    void tests_keepEachTestWithItsRequestWhenNodesAreTakenWithoutExecutingThem() throws Throwable {
        // given - every node taken first, as a tool listing the tests would
        var stream = fixture("bridge/slow.brat.yaml").tests();
        var tests = new ArrayList<DynamicTest>();
        for (var nodes = stream.iterator(); nodes.hasNext(); ) {
            tests.add((DynamicTest) nodes.next());
        }

        // when - executed afterwards, last first
        var verdicts = new ArrayList<String>();
        for (var i = tests.size() - 1; i >= 0; i--) {
            tests.get(i).getExecutable().execute();
            verdicts.add(tests.get(i).getDisplayName());
        }
        stream.close();

        // then
        assertThat(verdicts).containsExactly("clean up [teardown]", "after", "slow", "first");
    }

    @Test
    void tests_readTheWholeStreamWithoutAnythingLeftRunning() throws Throwable {
        // given
        var stream = fixture("bridge/slow.brat.yaml").tests();

        // when
        try (stream) {
            runAll(stream.iterator());
        }

        // then
        assertThat(server.received()).containsExactly("/ok?first", "/slow", "/ok?after", "/ok?clean-up");
        assertThat(List.copyOf(server.received())).hasSize(4);
    }
}
