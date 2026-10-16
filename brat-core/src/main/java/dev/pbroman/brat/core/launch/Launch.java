package dev.pbroman.brat.core.launch;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.util.Require;

/**
 * What a run is launched with: a suite, optionally an environment, and launch parameters.
 * <p>
 * Everything is held as given — a path relative to the working directory, an absolute one, either
 * behind {@code file:}, or a {@code classpath:} location for the suite — and only resolved when the
 * launch is read. The suite is a file, or a directory holding one. The environment is named one of two
 * ways, never both: as a <strong>name</strong>, meaning the directory of that name beside the suite, or
 * as a <strong>directory</strong> anywhere. Built with {@link #of(String)} and the {@code with…}
 * methods, each returning a new launch:
 *
 * <pre>{@code
 * var launch = Launch.of("orders-api")              // the one suite in orders-api/
 *         .withEnvironment("dev")                   // orders-api/dev/
 *         .withParams(Map.of("timeout", "20000"));
 * }</pre>
 *
 * @param suite the suite file, or a directory holding exactly one, as given; never {@code null} or
 *        blank
 * @param environmentDirectory the environment directory, as given, or {@code null}; never blank, and
 *        never set together with {@code environment}
 * @param environment the name of the environment directory beside the suite, or {@code null}; never
 *        blank, never a path, and never set together with {@code environmentDirectory}. With neither, a
 *        suite runs with an empty {@code env} and environment-variable secrets only
 * @param params the launch parameters, read as {@code ${params.x}} or, prefixed with a namespace, as
 *        overrides; never {@code null}, unmodifiable, holding no {@code null} key or value
 */
public record Launch(String suite, String environmentDirectory, String environment, Map<String, String> params) {

    /**
     * Validates and copies the arguments.
     *
     * @param suite the suite file or directory, as given
     * @param environmentDirectory the environment directory, as given, or {@code null}
     * @param environment the environment's name, or {@code null}
     * @param params the launch parameters
     * @throws BratException if {@code suite} is {@code null} or blank; if {@code environmentDirectory}
     *         is blank; if {@code environment} is blank, holds a {@code /} or {@code \}, or is
     *         {@code .} or {@code ..} — a name, not a path, the message pointing at
     *         {@code environmentDirectory} for a path; if both {@code environmentDirectory} and
     *         {@code environment} are set, naming both; or if {@code params} is {@code null} or holds a
     *         {@code null} key or value
     */
    public Launch {
        Require.nonBlank(suite, "The suite to launch must not be blank");
        if (environmentDirectory != null) {
            Require.nonBlank(environmentDirectory, "The environment directory must not be blank");
        }
        if (environment != null) {
            Require.nonBlank(environment, "The environment name must not be blank");
            if (environment.contains("/")
                    || environment.contains("\\")
                    || ".".equals(environment)
                    || "..".equals(environment)) {
                throw new BratException("The environment '" + environment + "' is a path, not a name: an environment "
                        + "name means the directory of that name beside the suite. Name any other directory as "
                        + "the environment directory instead");
            }
        }
        if (environmentDirectory != null && environment != null) {
            throw new BratException("A launch names an environment or an environment directory, not both: '"
                    + environment + "' and '" + environmentDirectory + "'");
        }
        Require.nonNull(params, "The launch params must not be null");
        for (var entry : params.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new BratException(
                        "Launch params must not hold a null key or value, but '" + entry.getKey() + "' does");
            }
        }
        params = Collections.unmodifiableMap(new LinkedHashMap<>(params));
    }

    /**
     * A launch of {@code suite}, with no environment and no parameters.
     *
     * @param suite the suite file, or a directory holding exactly one, as given; must not be
     *        {@code null} or blank
     * @return the launch
     * @throws BratException if {@code suite} is {@code null} or blank
     */
    public static Launch of(String suite) {
        return new Launch(suite, null, null, Map.of());
    }

    /**
     * A copy of this launch reading {@code directory} as its environment, in place of any environment it
     * named before.
     *
     * @param directory the environment directory, as given; must not be {@code null} or blank
     * @return a new launch, identical but for its environment
     * @throws BratException if {@code directory} is {@code null} or blank
     */
    public Launch withEnvironmentDirectory(String directory) {
        Require.nonBlank(directory, "The environment directory must not be blank");
        return new Launch(suite, directory, null, params);
    }

    /**
     * A copy of this launch reading the directory named {@code name} beside the suite as its environment,
     * in place of any environment it named before.
     *
     * @param name the environment's name, such as {@code dev}; must not be {@code null} or blank, and must
     *        be a name rather than a path
     * @return a new launch, identical but for its environment
     * @throws BratException if {@code name} is {@code null} or blank, or is a path, as the constructor
     *         says
     */
    public Launch withEnvironment(String name) {
        Require.nonBlank(name, "The environment name must not be blank");
        return new Launch(suite, null, name, params);
    }

    /**
     * A copy of this launch carrying {@code newParams} instead of its current parameters.
     *
     * @param newParams the launch parameters; must not be {@code null} or hold a {@code null} key or
     *        value. Copied
     * @return a new launch, identical but for its parameters
     * @throws BratException if {@code newParams} is {@code null} or holds a {@code null} key or value
     */
    public Launch withParams(Map<String, String> newParams) {
        return new Launch(suite, environmentDirectory, environment, newParams);
    }
}
