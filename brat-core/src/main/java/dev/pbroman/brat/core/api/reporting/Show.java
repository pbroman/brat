package dev.pbroman.brat.core.api.reporting;

/**
 * For which requests a report shows one item of detail — the request as sent, the response, or the
 * assertions.
 * <p>
 * Written in a reporter's arguments in lower case: {@code failures}, {@code all}, {@code none}.
 *
 * @see ReportDetail
 */
public enum Show {

    /** Only where something failed. What counts as failed depends on the item; see {@link ReportDetail}. */
    FAILURES,

    /** Wherever there is something to show. */
    ALL,

    /** Never. */
    NONE
}
