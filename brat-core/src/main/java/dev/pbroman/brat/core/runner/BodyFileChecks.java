package dev.pbroman.brat.core.runner;

import java.util.ArrayList;
import java.util.List;

import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.data.TestSuite;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.util.Require;
import dev.pbroman.brat.core.util.ResourceReader;

import static dev.pbroman.brat.core.interpolation.InterpolationPatterns.TOKEN_PREFIX;
import static dev.pbroman.brat.core.util.Constants.FILE_BODY;
import static dev.pbroman.brat.core.util.Constants.PATH_DELIMITER;

/**
 * The body files a suite names, checked once before the run starts.
 *
 * <p>A {@code body: {file: …}} path holding no {@code ${…}} is knowable before anything executes, so
 * a typo in one fails here rather than forty requests into a run. A path built from a token is not
 * knowable until the request that uses it, and is left to that request.
 *
 * <p>⚠ <strong>Not a validation pass</strong>, which this project deliberately does not have, and the
 * distinction is the one {@code SuiteChecks} already draws: a validation pass inspects data it could
 * otherwise have used, while this checks a <em>file the structure names</em> — addressability, one
 * step later than the loader only because the suite's location is not known until a run is launched.
 */
public final class BodyFileChecks {

    private BodyFileChecks() {
        // utility class
    }

    /**
     * Checks that every body file a suite names with a token-free path exists.
     *
     * <p>The whole tree is walked, sub-suites included, and <strong>every</strong> missing file is
     * reported in one exception rather than only the first: the run is not going to start either way,
     * and reporting one typo at a time costs a launch per typo.
     *
     * <p>A path holding a {@code ${...}} token is skipped, not resolved — it may name a different file per
     * environment, and a check against its unresolved text would fail on a suite that is correct.
     *
     * @param suite the suite about to run; never {@code null}
     * @param suiteLocation the location the suite was loaded from, prefix and all, or {@code null}
     *        when it came from no location. A {@code null} does not by itself fail: it fails only if
     *        some request names a bare path, which is then unresolvable
     * @throws dev.pbroman.brat.core.exception.BratException if {@code suite} is {@code null}, or if
     *         any token-free body file cannot be resolved or does not exist. The message names every
     *         offending file and the path of the request that names it, and quotes no file content
     */
    public static void check(TestSuite suite, String suiteLocation) {
        Require.nonNull(suite, "The suite to check must not be null");
        var unreadable = new ArrayList<String>();
        collect(suite, suite.name(), suiteLocation, unreadable);
        if (!unreadable.isEmpty()) {
            throw new BratException("The suite names body files that cannot be read:" + String.join("", unreadable));
        }
    }

    /**
     * Walks one suite and its sub-suites, adding a line per body file that cannot be read.
     *
     * @param suite the suite to walk
     * @param path the path of {@code suite}, for naming the request in a message
     * @param suiteLocation where the suite document was loaded from, or {@code null}
     * @param unreadable collects one line per offending file, mutated
     */
    private static void collect(TestSuite suite, String path, String suiteLocation, List<String> unreadable) {
        for (var request : suite.requests()) {
            if (request.requestDefinition() instanceof HttpRequestDefinition definition) {
                checkBody(definition, path + PATH_DELIMITER + request.name(), suiteLocation, unreadable);
            }
        }
        for (var subSuite : suite.subSuites()) {
            collect(subSuite, path + PATH_DELIMITER + subSuite.name(), suiteLocation, unreadable);
        }
    }

    /**
     * Checks one request's body file, if it declares one with a path that can be known now.
     *
     * @param definition the request definition, as authored
     * @param requestPath the path of the request declaring it
     * @param suiteLocation where the suite document was loaded from, or {@code null}
     * @param unreadable collects one line per offending file, mutated
     */
    private static void checkBody(
            HttpRequestDefinition definition, String requestPath, String suiteLocation, List<String> unreadable) {
        var body = definition.getBody();
        if (body == null) {
            return;
        }
        var location = body.get(FILE_BODY);
        // A path holding a token may name a different file per environment, so its unresolved text
        // proves nothing: that one is left to the request that uses it.
        if (location == null || location.contains(TOKEN_PREFIX)) {
            return;
        }
        try {
            var resolved = ResourceReader.resolve(location, suiteLocation);
            if (!ResourceReader.exists(resolved)) {
                unreadable.add(line(requestPath, resolved, "not found, or not readable"));
            }
        } catch (BratException e) {
            unreadable.add(line(requestPath, location, e.getMessage()));
        }
    }

    /**
     * One line of the report, naming the request, the file and why it cannot be read.
     *
     * @param requestPath the path of the request declaring the file
     * @param location the file's location, as far as it could be resolved
     * @param reason why it cannot be read
     * @return the line, newline-prefixed
     */
    private static String line(String requestPath, String location, String reason) {
        return String.format("%n  - %s: %s (%s)", requestPath, location, reason);
    }
}
