package dev.pbroman.brat.junit;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.launch.Launch;
import dev.pbroman.brat.core.launch.LaunchReader;
import dev.pbroman.brat.core.runner.Brat;
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

    private BratTests(String suite, String environment, String environmentDirectory, Map<String, String> params) {
        this.suite = suite;
        this.environment = environment;
        this.environmentDirectory = environmentDirectory;
        this.params = Collections.unmodifiableMap(new LinkedHashMap<>(params));
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
        return new BratTests(path, null, null, Map.of());
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
        return new BratTests(suite, name, null, params);
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
        return new BratTests(suite, null, path, params);
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
        return new BratTests(suite, environment, environmentDirectory, next);
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
     * plugins on the classpath, and that runner is closed when its run ends.
     *
     * @return the suite's tests: one container per sub-suite, one test per request, in the order they
     *         run
     * @throws BratException if the suite or the environment directory cannot be found, naming every
     *         directory looked in; if the suite cannot be read or loaded, or the environment cannot be
     *         read — each as {@code brat run} reports it
     */
    public Stream<DynamicNode> tests() {
        var brat = Brat.builder().build();
        try {
            var prepared = new LaunchReader(brat.loader(), brat.environmentReader())
                    .read(launch(Path.of("").toAbsolutePath()));
            return RunBridge.nodes(brat, prepared);
        } catch (RuntimeException e) {
            brat.close();
            throw e;
        }
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
