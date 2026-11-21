package dev.pbroman.brat.core.rendering;

/**
 * The words a report's lines open with, so that every report — the console, a test's output — prints
 * the same ones.
 */
public final class ReportMarkers {

    /** A request that passed, or an assertion that held. */
    public static final String PASS = "PASS";

    /** A request that failed but did not error, or an assertion of severity {@code FAIL} that did not hold. */
    public static final String FAIL = "FAIL";

    /** A request that errored, or a run that ended in an error. */
    public static final String ERROR = "ERROR";

    /** A request that was skipped. */
    public static final String SKIP = "SKIP";

    /** An assertion of severity {@code WARN} that did not hold. */
    public static final String WARN = "WARN";

    /** A suite that was skipped. */
    public static final String SKIPPED = "SKIPPED";

    /** A suite, or a run, that was aborted. */
    public static final String ABORTED = "ABORTED";

    /** A suite, or a run, that was cancelled. */
    public static final String CANCELLED = "CANCELLED";

    /** A run in which nothing failed. */
    public static final String PASSED = "PASSED";

    /** A run in which a request failed or a suite was aborted. */
    public static final String FAILED = "FAILED";

    /** A capture that failed. */
    public static final String CAPTURE = "CAPTURE";

    /** A redirect that was not followed. */
    public static final String REDIRECT = "REDIRECT";

    private ReportMarkers() {
        // constants only
    }
}
