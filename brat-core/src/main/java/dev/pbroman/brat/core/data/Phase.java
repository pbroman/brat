package dev.pbroman.brat.core.data;

/**
 * When a request runs relative to everything else under the suite that declares it.
 * <p>
 * A marker, not an ordering number: a {@link #SETUP} request runs before the suite's other requests
 * and all its subSuites, and a {@link #TEARDOWN} request after them. Position in the document does
 * not matter; the marker does. Only requests carry a phase — a suite runs where it is declared.
 */
public enum Phase {

    /**
     * Runs before everything else under the declaring suite, whatever the declaration order. A setup
     * request that fails aborts that suite.
     */
    SETUP,

    /** The default: runs in declaration order, after setup and before teardown. */
    MAIN,

    /** Runs after everything else under the declaring suite, in declaration order. */
    TEARDOWN
}
