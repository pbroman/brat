package dev.pbroman.brat.integration.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.data.TestSuite;
import dev.pbroman.brat.core.data.result.AssertionResult;
import dev.pbroman.brat.core.data.result.RequestResult;
import dev.pbroman.brat.core.data.result.RunResult;
import dev.pbroman.brat.core.handler.ApacheHttpRequestHandler;
import dev.pbroman.brat.core.loader.SuiteLoader;
import dev.pbroman.brat.core.runner.Brat;
import dev.pbroman.brat.core.runner.Environment;
import dev.pbroman.brat.core.util.ResourceReader;
import dev.pbroman.brat.integration.app.CrudApp;
import org.junit.jupiter.api.BeforeEach;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What every aspect class needs: a running server, a hand-wired runner, a clean database, and the
 * two lines that turn a YAML resource into a {@link RunResult}.
 *
 * <p><strong>Hand-wired is the point.</strong> The runner below is assembled by a caller holding
 * nothing but {@code brat-core} — no framework, no container, no auto-configuration. That is the
 * reversibility guarantee this module exists to keep testable, so nothing here may acquire a runner
 * any other way.
 *
 * <p>The server and the request handler are shared for the whole module run; the database is not, and
 * {@link #clearDatabase()} empties it before every test.
 */
public abstract class EndToEndTestBase {

    /** Shared for the module run: one pooled client, closed when the test JVM exits. */
    private static final ApacheHttpRequestHandler HANDLER = closedAtExit(new ApacheHttpRequestHandler());

    private static final SuiteLoader LOADER = new SuiteLoader();

    /** The hand-wired runner under test. */
    protected static final Brat BRAT = Brat.builder().requestHandler(HANDLER).build();

    /** Seeds and inspects the server without going through BRAT. */
    protected final CrudClient crud = new CrudClient(baseUrl());

    /**
     * Where the server under test is listening, booting it on the first call.
     *
     * <p>A method rather than a {@code static final} field on purpose: a field would boot the server
     * inside this class's initializer, and a failure there poisons the class — every test in every
     * subclass then reports {@code NoClassDefFoundError} instead of what went wrong.
     *
     * @return the base URL, with no trailing slash
     */
    protected static String baseUrl() {
        return CrudApp.baseUrl();
    }

    /** In-memory state is shared, so every test starts from an empty database. */
    @BeforeEach
    void clearDatabase() {
        crud.clear();
    }

    /**
     * Asserts that the run passed <em>and</em> that every assertion the suite declares actually ran.
     *
     * <p><strong>{@code failed()} alone is not evidence.</strong> A run whose assertions never ran
     * reports {@code false} too, so a test asserting only that would stay green if assertion resolution
     * broke entirely — the exact class of false green this module exists to catch.
     *
     * <p><strong>The expected count comes from the suite, not from the test.</strong> A literal here
     * would be the suite's assertion count written down a second place, and editing a fixture would
     * silently leave it stale. Counting the authored assertions instead makes this the stronger claim —
     * every assertion the author wrote produced a result — and it needs no maintenance.
     *
     * <p>It therefore applies to a suite in which <strong>every request runs to the end</strong>: a
     * skipped request, an errored one or a give-up produces fewer results than the document declares,
     * and such a run is asserted on directly rather than through this. Top-level requests only, since
     * that is all this runner walks.
     *
     * <p>A suite declaring <strong>no</strong> assertions is rejected rather than passing: an expected
     * count of zero makes both checks below vacuous, which would quietly return this helper to the
     * false green it exists to prevent.
     *
     * @param result the run to check
     * @param suite the suite that was run, and the source of how many assertions to expect
     */
    protected static void assertPassed(RunResult result, TestSuite suite) {
        var authored = authoredAssertions(suite);
        assertThat(authored)
                .as(
                        "'%s' declares no assertions, so asserting it passed would assert nothing; "
                                + "assert on the results directly",
                        suite.name())
                .isPositive();
        assertThat(assertionResultsOf(result)).hasSize(authored).allMatch(AssertionResult::passed);
        assertThat(result.failed()).isFalse();
    }

    /**
     * Every assertion result of the run, across its requests, in execution order.
     *
     * <p>The one traversal from a {@link RunResult} down to its assertion results, shared so a test
     * asking a different question of them — which ones failed, what they said — does not write the
     * three stages again.
     *
     * @param result the run to read
     * @return the assertion results
     */
    protected static List<AssertionResult> assertionResultsOf(RunResult result) {
        return result.requestResults().stream()
                .map(RequestResult::responseActionsResult)
                .flatMap(actions -> actions.assertionResults().stream())
                .toList();
    }

    /**
     * How many assertions the suite's own requests declare between them.
     *
     * @param suite the suite to count
     * @return the number of authored assertions
     */
    private static int authoredAssertions(TestSuite suite) {
        var authored = 0;
        for (var request : suite.requests()) {
            if (request.responseActions() != null) {
                authored += request.responseActions().getAssertions().size();
            }
        }
        return authored;
    }

    /**
     * Loads a suite from a classpath resource, the way a consumer loads one: content first.
     *
     * @param resource the resource path, e.g. {@code suites/one-request.yaml}
     * @return the loaded suite, whose errors cite {@code resource} as their origin
     */
    protected static TestSuite suite(String resource) {
        return LOADER.load(ResourceReader.readFileToString(resource), resource);
    }

    /**
     * The launch environment: {@code baseUrl} plus whatever this test seeded.
     *
     * <p><strong>{@code baseUrl} is this module's to set.</strong> A caller passing one is rejected
     * rather than silently redirecting every request of the suite somewhere else — a mistake whose only
     * symptom would be an unexplained connection failure. A test needing a second host gives it its own
     * key and a suite reads both, which is what {@code two-hosts.yaml} does with {@code deadUrl}.
     *
     * @param env further {@code env} entries, typically ids the fixture just created
     * @return an environment with no secrets configured
     * @throws IllegalArgumentException if {@code env} holds a {@code baseUrl} of its own
     */
    protected static Environment environment(Map<String, Object> env) {
        if (env.containsKey("baseUrl")) {
            throw new IllegalArgumentException(
                    "baseUrl is set from the running server; give a second host its own key, as two-hosts.yaml does");
        }
        var all = new LinkedHashMap<String, Object>();
        all.put("baseUrl", baseUrl());
        all.putAll(env);
        return Environment.of(all, Map.of());
    }

    /**
     * Loads a suite and runs it — the whole slice in one call.
     *
     * @param resource the suite resource to load
     * @param env further {@code env} entries beyond {@code baseUrl}
     * @return what the run produced
     */
    protected static RunResult run(String resource, Map<String, Object> env) {
        return run(suite(resource), env);
    }

    /**
     * Runs a suite the test already holds, which is what {@link #assertPassed(RunResult, TestSuite)}
     * needs: the suite that ran is also the one the expected assertion count is read from.
     *
     * @param suite the suite to run
     * @param env further {@code env} entries beyond {@code baseUrl}
     * @return what the run produced
     */
    protected static RunResult run(TestSuite suite, Map<String, Object> env) {
        return BRAT.run(suite, environment(env));
    }

    /**
     * Registers the handler's pooled client for closing at JVM exit.
     *
     * <p>A shutdown hook rather than an {@code @AfterAll}: the handler is shared by every aspect
     * class, so the first class to finish must not close it. Who owns a handler's lifecycle in
     * production is a separate, still-open question.
     *
     * @param handler the handler to close at exit
     * @return the same handler
     */
    private static ApacheHttpRequestHandler closedAtExit(ApacheHttpRequestHandler handler) {
        Runtime.getRuntime().addShutdownHook(new Thread(handler::close));
        return handler;
    }
}
