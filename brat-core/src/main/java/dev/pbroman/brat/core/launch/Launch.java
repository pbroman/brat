package dev.pbroman.brat.core.launch;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.util.Require;

/**
 * What a run is launched with: a suite file, optionally an environment directory, and launch
 * parameters.
 * <p>
 * Locations are held as given — a path relative to the working directory, an absolute one, either
 * behind {@code file:}, or a {@code classpath:} location for the suite — and are only resolved when
 * the launch is read. Built with {@link #of(String)} and the {@code with…} methods, each returning a
 * new launch:
 *
 * <pre>{@code
 * var launch = Launch.of("orders-api/orders.brat.yaml")
 *         .withEnvironmentDirectory("orders-api/dev")
 *         .withParams(Map.of("timeout", "20000"));
 * }</pre>
 *
 * @param suite the suite file, as given; never {@code null} or blank
 * @param environmentDirectory the environment directory, as given, or {@code null} for none — a suite
 *        that needs no environment runs with an empty {@code env} and environment-variable secrets
 *        only; never blank
 * @param params the launch parameters, read as {@code ${params.x}} or, prefixed with a namespace, as
 *        overrides; never {@code null}, unmodifiable, holding no {@code null} key or value
 */
public record Launch(String suite, String environmentDirectory, Map<String, String> params) {

    /**
     * Validates and copies the arguments.
     *
     * @param suite the suite file, as given
     * @param environmentDirectory the environment directory, as given, or {@code null}
     * @param params the launch parameters
     * @throws BratException if {@code suite} is {@code null} or blank, if {@code environmentDirectory}
     *         is blank, or if {@code params} is {@code null} or holds a {@code null} key or value
     */
    public Launch {
        Require.nonBlank(suite, "The suite to launch must not be blank");
        if (environmentDirectory != null) {
            Require.nonBlank(environmentDirectory, "The environment directory must not be blank");
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
     * A launch of {@code suite}, with no environment directory and no parameters.
     *
     * @param suite the suite file, as given; must not be {@code null} or blank
     * @return the launch
     * @throws BratException if {@code suite} is {@code null} or blank
     */
    public static Launch of(String suite) {
        return new Launch(suite, null, Map.of());
    }

    /**
     * A copy of this launch reading {@code directory} as its environment.
     *
     * @param directory the environment directory, as given; must not be {@code null} or blank
     * @return a new launch, identical but for its environment directory
     * @throws BratException if {@code directory} is {@code null} or blank
     */
    public Launch withEnvironmentDirectory(String directory) {
        Require.nonBlank(directory, "The environment directory must not be blank");
        return new Launch(suite, directory, params);
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
        return new Launch(suite, environmentDirectory, newParams);
    }
}
