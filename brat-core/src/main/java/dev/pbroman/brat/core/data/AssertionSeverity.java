package dev.pbroman.brat.core.data;

/**
 * How much a failing {@link Assertion} matters, declared by the suite author.
 * <p>
 * The constants are ordered least severe first, so a future, more severe value (one that aborts the
 * run rather than only failing an assertion) appends without disturbing the ladder.
 * <p>
 * Nothing in {@code brat-core} acts on this yet: an {@link dev.pbroman.brat.core.api.resolver.AssertionResolver}
 * records it on every {@link dev.pbroman.brat.core.data.result.AssertionResult} it produces, and the
 * runner that aggregates those results decides what each value means for a run.
 */
public enum AssertionSeverity {

    /**
     * The assertion is recorded when it fails, but the failure is not meant to fail the run.
     */
    WARN,

    /**
     * The assertion fails when its condition is false. This is the default.
     * <p>
     * It fails <em>that assertion</em> only — the suite still runs to completion and reports every
     * further failure, which is what the conversion boundary in
     * {@link dev.pbroman.brat.core.handler.ResponseActionsHandler} exists for.
     */
    FAIL
}
