package dev.pbroman.brat.core.data.result;

/**
 * How a suite's walk ended — why it stopped, and nothing else.
 * <p>
 * It carries no counts and no verdict: every request beneath the suite has already reported its own
 * result, and a listener that wants totals counts those. What the requests cannot say is why there are
 * none — a skipped suite, an aborted one and an empty one all emit no request events — and that is
 * what this answers.
 */
public sealed interface SuiteStatus {

    /**
     * The suite was walked to the end: its setup, its main phase and its teardown all ran. Says nothing
     * about whether anything beneath it failed.
     */
    record Completed() implements SuiteStatus {}

    /**
     * The suite's skip condition held, so nothing beneath it was walked.
     *
     * @param reason why it was skipped, for the report; never {@code null}
     */
    record Skipped(String reason) implements SuiteStatus {}

    /**
     * The walk abandoned the suite before finishing it — at entry, or when one of its own setup
     * requests failed —
     * because something showed the environment is not fit to run what is beneath it. Its siblings are
     * unaffected.
     *
     * @param reason what went wrong, naming the cause; never {@code null}
     */
    record Aborted(String reason) implements SuiteStatus {}

    /**
     * The run was cancelled while this suite was being walked, so it stopped before finishing. Nothing
     * it had left to run was started, its teardown included. Says nothing about the suite itself — the
     * stop came from outside it.
     */
    record Cancelled() implements SuiteStatus {}
}
