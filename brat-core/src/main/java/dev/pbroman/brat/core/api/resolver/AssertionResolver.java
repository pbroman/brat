package dev.pbroman.brat.core.api.resolver;

import java.util.List;

import dev.pbroman.brat.core.data.Assertion;
import dev.pbroman.brat.core.data.result.AssertionResult;
import dev.pbroman.brat.core.data.runtime.RuntimeData;

/**
 * Interface for assertion resolvers.
 */
public interface AssertionResolver {

    /**
     * Resolves an {@link Assertion}, testing if all contained conditions are true.
     * <p>
     * <strong>No failure of the assertion's own interpolation or resolution escapes.</strong>
     * Whatever goes wrong in either — an unresolvable token, an unrecognized {@code func}, a func
     * given an operand of the wrong shape, or a rule throwing something unplanned — is reported as a
     * failed {@link AssertionResult} rather than thrown, so a suite runs to completion and reports
     * every failure instead of aborting on the first. An implementation must therefore catch
     * {@link Exception}, not merely {@code BratException}: the rules it delegates to are plugin
     * extension points, and their exception types are not core's to enumerate. {@link Error} is not
     * caught and still propagates.
     * <p>
     * A failure whose cause is a {@code BratException} reports that exception's message as written,
     * since it was phrased for a suite author. Any other exception additionally names its type, so a
     * defect in core or in a plugin stays distinguishable from an authoring mistake rather than
     * being silently reported as one.
     *
     * @param assertion the {@link Assertion} to resolve, as authored — never an interpolated copy
     * @param runtimeData the object containing values
     * @return one result per condition resolved, never {@code null} and never empty; a failed result
     *         carries {@code false} and the failure's message
     * @throws dev.pbroman.brat.core.exception.BratException for a <em>wiring</em> error — one that is
     *         the caller's mistake rather than the suite author's, such as being handed an assertion
     *         that is already an interpolated copy. Such an error is deliberately not converted to a
     *         result, because a result would report it against the author
     * @throws RuntimeException for a <em>structural</em> failure in the implementation's own
     *         preparation of a condition to test, which is neither interpolating nor resolving that
     *         condition. Such a failure is not converted either, and here the reason is a type rather
     *         than a policy: {@link AssertionResult} requires a non-null condition, so a failure to
     *         produce one leaves nothing to report the failure against
     */
    List<AssertionResult> resolve(Assertion assertion, RuntimeData runtimeData);
}
