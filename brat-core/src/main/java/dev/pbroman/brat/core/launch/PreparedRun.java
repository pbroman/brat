package dev.pbroman.brat.core.launch;

import dev.pbroman.brat.core.data.TestSuite;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.util.Require;

/**
 * A launch, read: the suite to run and the environment to run it against, joined.
 * <p>
 * The environment carries the suite's location, so a bare path inside the suite resolves next to the
 * suite file.
 *
 * @param suite the suite; never {@code null}
 * @param environment the environment, its {@code suiteLocation} set to where {@code suite} was read
 *        from; never {@code null}
 */
public record PreparedRun(TestSuite suite, Environment environment) {

    /**
     * Validates the arguments.
     *
     * @param suite the suite
     * @param environment the environment
     * @throws BratException if either is {@code null}
     */
    public PreparedRun {
        Require.nonNull(suite, "The suite must not be null");
        Require.nonNull(environment, "The environment must not be null");
    }
}
