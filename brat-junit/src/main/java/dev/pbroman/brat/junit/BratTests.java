package dev.pbroman.brat.junit;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

import dev.pbroman.brat.core.api.reporting.ReportDetail;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.launch.Launch;
import dev.pbroman.brat.core.launch.LaunchReader;
import dev.pbroman.brat.core.util.Require;
import org.junit.jupiter.api.DynamicNode;

/**
 * A suite to run as JUnit tests, returned from a {@code @TestFactory} method: each request a test, each
 * sub-suite a group.
 *
 * <pre>
 * &#64;TestFactory
 * Stream&lt;DynamicNode&gt; ordersApi() {
 *     return BratTests.suite("api-tests/orders-api")
 *             .env("baseUrl", "http://localhost:" + port)
 *             .tests();
 * }
 * </pre>
 *
 * It takes what {@code brat run} takes — a suite, an environment, launch parameters — and is immutable:
 * every method but {@link #tests()} returns a new instance, so one held in a field can be shared by
 * several factory methods.
 */
public final class BratTests {

    private final String suite;
    private final String environment;
    private final String environmentDirectory;
    private final Map<String, String> params;
    private final Map<String, String> shown;
    private final Boolean quiet;

    private BratTests(
            String suite,
            String environment,
            String environmentDirectory,
            Map<String, String> params,
            Map<String, String> shown,
            Boolean quiet) {
        this.suite = suite;
        this.environment = environment;
        this.environmentDirectory = environmentDirectory;
        this.params = Collections.unmodifiableMap(new LinkedHashMap<>(params));
        this.shown = Collections.unmodifiableMap(new LinkedHashMap<>(shown));
        this.quiet = quiet;
    }

    /**
     * Starts with the suite at {@code path}: a suite file, or a directory holding exactly one, as for
     * {@code brat run}. A relative path is found from the working directory up to the repository's root,
     * as {@link #tests()} describes.
     *
     * @param path the suite; must not be {@code null} or blank
     * @return the tests of that suite, with no environment and no parameters
     * @throws BratException if {@code path} is {@code null} or blank
     */
    public static BratTests suite(String path) {
        Require.nonBlank(path, "The suite to run must not be blank");
        return new BratTests(path, null, null, Map.of(), Map.of(), null);
    }

    /**
     * Starts with the suite at {@code path}, as {@link #suite(String)} does.
     *
     * @param path the suite; must not be {@code null}
     * @return the tests of that suite, with no environment and no parameters
     * @throws BratException if {@code path} is {@code null} or empty
     */
    public static BratTests suite(Path path) {
        Require.nonNull(path, "The suite to run must not be null");
        return suite(path.toString());
    }

    /**
     * Returns these tests run against the environment directory named {@code name}, beside the suite —
     * {@code brat run}'s {@code -e}. Replaces an environment directory given before.
     *
     * @param name the environment's name; must not be {@code null} or blank
     * @return the new instance
     * @throws BratException if {@code name} is {@code null} or blank
     */
    public BratTests environment(String name) {
        Require.nonBlank(name, "The environment name must not be blank");
        return new BratTests(suite, name, null, params, shown, quiet);
    }

    /**
     * Returns these tests run against the environment directory at {@code path} — {@code brat run}'s
     * {@code --env-dir}. A relative path is found as the suite's is. Replaces an environment named
     * before.
     *
     * @param path the environment directory; must not be {@code null} or blank
     * @return the new instance
     * @throws BratException if {@code path} is {@code null} or blank
     */
    public BratTests environmentDirectory(String path) {
        Require.nonBlank(path, "The environment directory must not be blank");
        return new BratTests(suite, null, path, params, shown, quiet);
    }

    /**
     * Returns these tests with the {@code env} value {@code key} set to {@code value}, winning over the
     * environment's — the way a test hands over the url of the app it started. The same as
     * {@code param("env." + key, value)}.
     *
     * @param key the {@code env} key, as the suite reads it ({@code baseUrl} for {@code ${env.baseUrl}});
     *        must not be {@code null} or blank
     * @param value its value; must not be {@code null}
     * @return the new instance
     * @throws BratException if {@code key} is {@code null} or blank, or {@code value} is {@code null}
     */
    public BratTests env(String key, String value) {
        Require.nonBlank(key, "The env key must not be blank");
        return param("env." + key, value);
    }

    /**
     * Returns these tests with the launch parameter {@code key} set to {@code value} — {@code brat run}'s
     * {@code -p key=value}: {@code ${params.key}}, or, prefixed with a namespace, an override
     * ({@code secrets.password}, {@code vars.accessToken}). A key given before is replaced.
     *
     * @param key the parameter's key; must not be {@code null} or blank
     * @param value its value; must not be {@code null}
     * @return the new instance
     * @throws BratException if {@code key} is {@code null} or blank, or {@code value} is {@code null}
     */
    public BratTests param(String key, String value) {
        Require.nonBlank(key, "The parameter key must not be blank");
        Require.nonNull(value, "The value of parameter '" + key + "' must not be null");
        var next = new LinkedHashMap<>(params);
        next.put(key, value);
        return new BratTests(suite, environment, environmentDirectory, next, shown, quiet);
    }

    /**
     * Returns these tests showing more, or less, beneath each request — what {@code brat run}'s
     * {@code --show-*} options choose, by the same keys and values: {@code response} and {@code request}
     * take {@code failures}, {@code all} or {@code none}; {@code assertions} takes {@code failures} or
     * {@code all}, since a failed assertion is always shown; {@code secrets} takes {@code mask} or
     * {@code show}. It decides what each test prints and what a
     * failed request's message carries. Keys given before stay unless given again.
     * <p>
     * Wins over the same choice made in {@code brat.show.<key>}, as a system property or in
     * {@code junit-platform.properties}.
     *
     * @param args the choices, by key; must not be {@code null}
     * @return the new instance
     * @throws BratException if {@code args} is {@code null}; for a key that is not one of the four,
     *         naming those there are; or for a value a key does not take, as {@code brat run} reports it
     */
    public BratTests show(Map<String, String> args) {
        Require.nonNull(args, "The details to show must not be null");
        var unknown = new ArrayList<String>();
        for (var key : args.keySet()) {
            if (!ReportDetail.KEYS.contains(key)) {
                unknown.add(key);
            }
        }
        if (!unknown.isEmpty()) {
            throw new BratException("Cannot show " + unknown + ": the details are " + ReportDetail.KEYS);
        }
        var next = new LinkedHashMap<>(shown);
        next.putAll(args);
        ReportDetail.of(next);
        return new BratTests(suite, environment, environmentDirectory, params, next, quiet);
    }

    /**
     * Returns these tests printing nothing while they execute. A failed request's message still carries
     * its detail. Wins over {@code brat.quiet}, as a system property or in
     * {@code junit-platform.properties}.
     *
     * @return the new instance
     */
    public BratTests quiet() {
        return new BratTests(suite, environment, environmentDirectory, params, shown, Boolean.TRUE);
    }

    /**
     * Reads the suite and returns its tests, for a {@code @TestFactory} method to return.
     * <p>
     * <strong>Finding the suite.</strong> A relative suite path, and a relative environment directory,
     * is looked for in the working directory, then in each directory above it, up to the repository's
     * root — the nearest directory holding {@code .git} — or, outside a repository, the filesystem root;
     * the first place it exists is used. An absolute one is used as given. An environment named with
     * {@link #environment(String)}, and every body file, is found beside the suite, as for
     * {@code brat run}.
     * <p>
     * <strong>Reading it happens now</strong>: the suite is loaded and the environment read before this
     * method returns, so a mistake in either fails the factory method itself. The returned stream runs
     * nothing until JUnit takes the first test from it. Each call builds a runner of its own, with the
     * plugins on the classpath, and that runner is closed when its run ends — or when the stream is
     * closed before its run started.
     * <p>
     * <strong>The tests.</strong> The suite runs once, on a thread of its own, while JUnit executes the
     * tests; a request's test is handed to JUnit when the request starts and ends when it finishes. JUnit
     * may execute them one after another or in parallel, as its configuration has it: each test waits for
     * its own request's result, and the run's order is the suite's either way. The
     * suite's own requests and sub-suites are the stream's elements, in the order they run; each sub-suite
     * is a {@link org.junit.jupiter.api.DynamicContainer} named after it, holding its own the same way. A
     * request's test is named as the console names it: its name, followed by {@code [setup]} or
     * {@code [teardown]} for those phases. A request that
     * <ul>
     *   <li>passed — a failed {@code WARN} assertion included — passes;</li>
     *   <li>failed an assertion or a capture, errored, or gave up polling, fails with an
     *       {@code AssertionFailedError}: a first line naming the request, followed by {@code — } and the
     *       reason where its status gives one, then what the console shows beneath it under the chosen
     *       detail ({@link #show}), each line indented by two spaces — secrets masked, as there;</li>
     *   <li>was skipped is aborted with the skip's reason, which JUnit reports as skipped.</li>
     * </ul>
     * A suite that was aborted ends with one more test, {@code <suite name>: aborted}, failing with the
     * reason; a suite that was skipped holds just one test, {@code <suite name>: skipped}, skipped with
     * the reason. For the suite itself these stand last in the stream. A run that ends in an error —
     * one a suite file cannot cause, such as a missing request handler — ends the stream with a failing
     * test {@code <suite name>: run failed}, carrying the error.
     * <p>
     * <strong>What a test prints</strong>, to standard output while it executes — so an IDE shows it on
     * that test and Surefire files it with that test's report: a request's line as {@code brat run}
     * prints it ({@code PASS   passes (12 ms)}), and beneath a request that did not fail what the chosen
     * detail shows of it; a failed request's detail is in its message instead. A suite's {@code aborted}
     * or {@code skipped} test prints {@code ABORTED <name> — <reason>} or
     * {@code SKIPPED <name> — <reason>}, and {@code run failed} prints {@code ERROR <name> — <error>}.
     * The detail is {@code brat run}'s default unless {@link #show} or {@code brat.show.<key>} chooses
     * otherwise; {@link #quiet()} or {@code brat.quiet=true} prints nothing. {@code brat.show.<key>} and
     * {@code brat.quiet} are read as system properties first, then from {@code junit-platform.properties}
     * on the test classpath; the test's own choice wins over both.
     * <p>
     * <strong>Stopping.</strong> If the thread executing a request's test is interrupted, or the stream is
     * closed before it has been read to its end, the run is stopped as Ctrl-C stops {@code brat run} —
     * no new request starts, and the teardown of what has started runs — and waited for. The interrupted
     * test fails, saying so; no further test is handed to JUnit for that run, teardown's included.
     *
     * @return the suite's tests: one container per sub-suite, one test per request, in the order they
     *         run
     * @throws BratException if the suite or the environment directory cannot be found, naming every
     *         directory looked in; if the suite cannot be read or loaded, or the environment cannot be
     *         read — each as {@code brat run} reports it; or if {@code brat.show.<key>} or
     *         {@code brat.quiet} is configured with a key or a value it does not take, naming where
     */
    public Stream<DynamicNode> tests() {
        var brat = RunBridge.newBrat();
        try {
            var prepared = new LaunchReader(brat.loader(), brat.environmentReader())
                    .read(launch(Path.of("").toAbsolutePath()));
            return RunBridge.nodes(
                    brat,
                    prepared,
                    output(
                            System.getProperties(),
                            Output.junitPlatformProperties(
                                    Thread.currentThread().getContextClassLoader())));
        } catch (RuntimeException e) {
            brat.close();
            throw e;
        }
    }

    /**
     * What these tests print, resolved against {@code system} and {@code file}.
     *
     * @param system the system properties
     * @param file the content of {@code junit-platform.properties}
     * @return the output
     */
    Output output(Properties system, Properties file) {
        return Output.resolve(shown, quiet, system, file);
    }

    /**
     * The launch these tests describe, with relative paths found from {@code workingDirectory}.
     *
     * @param workingDirectory where the search starts
     * @return the launch
     * @throws BratException if the suite or the environment directory cannot be found
     */
    Launch launch(Path workingDirectory) {
        var launch = Launch.of(SuiteLocator.locate(suite, workingDirectory).toString())
                .withParams(params);
        if (environment != null) {
            return launch.withEnvironment(environment);
        }
        if (environmentDirectory != null) {
            return launch.withEnvironmentDirectory(
                    SuiteLocator.locate(environmentDirectory, workingDirectory).toString());
        }
        return launch;
    }
}
