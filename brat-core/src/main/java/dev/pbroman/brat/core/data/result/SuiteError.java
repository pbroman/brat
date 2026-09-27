package dev.pbroman.brat.core.data.result;

/**
 * Why a suite was aborted: the run could not establish what that suite needed, so nothing beneath it
 * ran as the author wrote it.
 * <p>
 * The same value the suite's {@code SuiteExited} event carries as {@link SuiteStatus.Aborted}, recorded
 * on the {@link RunResult} for a consumer that did not listen to events. It is an error, not a failed
 * expectation: it says the run could not do what was asked, and it always fails the run.
 *
 * @param path the aborted suite's path
 * @param message why it was aborted, naming the cause
 */
public record SuiteError(String path, String message) {}
