package dev.pbroman.brat.core.api.interpolation;

import java.util.List;

import dev.pbroman.brat.core.exception.BratException;

/**
 * One function callable from a suite as {@code ${__name(arg, …)}}.
 * <p>
 * Arguments arrive fully resolved: a nested token has already been interpolated, so an implementation
 * never sees a {@code ${…}} and must never try to resolve one itself — doing so would re-interpret
 * data as a template, which is the injection hole the single-pass design exists to avoid.
 * <p>
 * Arguments and results are text. A function that conceptually produces a number, a date or a
 * structure formats it: the caller is putting the result into a request body, a header or a URL, or
 * comparing it with a condition, and all of those are text. Structured values come from the response
 * ({@code ${response.json.$.items}}), which already yields them.
 */
@FunctionalInterface
public interface BratFunction {

    /**
     * Applies the function to its arguments.
     *
     * @param args the resolved arguments, in the order written; never {@code null}, and empty for a
     *        no-argument call. Individual arguments may be empty strings but are never {@code null}
     * @return the result; {@code null} is not permitted — a function that has no answer throws
     *         instead, so that a missing value cannot be mistaken for a resolved one
     * @throws BratException if the arguments are the wrong number or kind for this function, or if
     *         producing the value fails
     */
    String apply(List<String> args);
}
