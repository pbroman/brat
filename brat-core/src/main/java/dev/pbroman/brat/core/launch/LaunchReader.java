package dev.pbroman.brat.core.launch;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.loader.SuiteLoader;
import dev.pbroman.brat.core.secrets.SecretsProviderConfig;
import dev.pbroman.brat.core.util.Require;
import dev.pbroman.brat.core.util.ResourceReader;

/**
 * Reads a {@link Launch} — the suite file and the environment directory it names — into a
 * {@link PreparedRun}.
 * <p>
 * This is where a location given at launch meets the rest of BRAT: both are resolved here, and the
 * suite's resolved location is handed to the environment, so nothing downstream sees a path as it was
 * given and no caller has to join the two itself.
 */
public final class LaunchReader {

    private final SuiteLoader loader;
    private final EnvironmentReader environmentReader;

    /**
     * Constructs a reader that loads suites with {@code loader} and reads environment directories with
     * {@code environmentReader} — typically a runner's own, so that what is read is what it can run.
     *
     * @param loader binds a suite document; must not be {@code null}
     * @param environmentReader reads an environment directory; must not be {@code null}
     * @throws BratException if either argument is {@code null}
     */
    public LaunchReader(SuiteLoader loader, EnvironmentReader environmentReader) {
        Require.nonNull(loader, "The suite loader must not be null");
        Require.nonNull(environmentReader, "The environment reader must not be null");
        this.loader = loader;
        this.environmentReader = environmentReader;
    }

    /**
     * Reads {@code launch}: its suite, then its environment.
     * <ol>
     *     <li><strong>The suite.</strong> Its location is resolved as a launch location — a path with
     *         no prefix is on the filesystem, relative to the working directory unless absolute;
     *         {@code file:} and {@code classpath:} are accepted — to a prefixed, absolute one. The
     *         document there is read and loaded, with that location as its origin, so a load error
     *         names it.</li>
     *     <li><strong>The environment.</strong> Read from the environment directory, if the launch
     *         names one; otherwise an empty {@code env} and a secrets configuration with no sources,
     *         which leaves environment variables as the only secrets. Either way it carries the
     *         launch's {@code params}.</li>
     *     <li><strong>The join.</strong> The environment's {@code suiteLocation} is the suite's
     *         resolved location, so a bare path in the suite — a body file, say — resolves next to
     *         the suite file, in the same medium.</li>
     * </ol>
     * Nothing is run, and no file a suite names is read here.
     *
     * @param launch the launch to read; must not be {@code null}
     * @return the suite and its environment, joined
     * @throws BratException if {@code launch} is {@code null}; if the suite location names no valid
     *         path or nothing can be read there — naming it; for anything the loader rejects, with the
     *         resolved location as the origin; or for anything reading the environment rejects,
     *         {@code ${...}} in a launch parameter included. The suite is read first, so a launch
     *         wrong in both reports the suite
     */
    public PreparedRun read(Launch launch) {
        Require.nonNull(launch, "The launch to read must not be null");
        var location = LaunchLocations.fileLocation(launch.suite());
        var suite = loader.load(ResourceReader.readFileToString(location), location);
        var environment = launch.environmentDirectory() == null
                ? new Environment(Map.of(), new LinkedHashMap<>(launch.params()), SecretsProviderConfig.empty())
                : environmentReader.read(launch.environmentDirectory(), launch.params());
        return new PreparedRun(suite, environment.withSuiteLocation(location));
    }
}
