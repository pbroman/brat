package dev.pbroman.brat.core.data;

/**
 * When a suite or request runs relative to its siblings.
 * <p>
 * A marker, not an ordering number: {@link #SETUP} runs before everything under the suite that
 * declares it — including sibling subSuites declared above it — and {@link #TEARDOWN} mirrors that,
 * last and in reverse. Position in the document does not matter; the marker does.
 */
public enum Phase {

    /** Runs before everything under the declaring suite, whatever the declaration order. */
    SETUP,

    /** The default: runs in declaration order, after setup and before teardown. */
    MAIN,

    /** Runs after everything under the declaring suite, in reverse. */
    TEARDOWN
}
