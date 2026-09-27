package dev.pbroman.brat.core.runner;

import java.util.ArrayList;
import java.util.Optional;

import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.data.TestSuite;
import dev.pbroman.brat.core.data.result.SuiteStatus;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.util.FailureMessages;
import dev.pbroman.brat.core.util.Require;

/**
 * Does what a suite declares for its own entry — its {@code setVars}, then its {@code skipCondition} —
 * and says whether the walk may go on into it.
 * <p>
 * <strong>Nothing about the suite escapes as an exception.</strong> A {@code setVars} entry or a skip
 * condition that cannot be evaluated says the run's own configuration is not fit for this subtree, so
 * it is answered as an abort for the walk to record, never thrown.
 */
class SuiteEntryEvaluator {

    private final Interpolation interpolation;
    private final ConditionEvaluator conditionEvaluator;

    /**
     * Constructs an evaluator over the collaborators it delegates to.
     *
     * @param interpolation resolves each {@code setVars} value
     * @param conditionEvaluator interpolates and answers the skip condition
     */
    SuiteEntryEvaluator(Interpolation interpolation, ConditionEvaluator conditionEvaluator) {
        this.interpolation = interpolation;
        this.conditionEvaluator = conditionEvaluator;
    }

    /**
     * Evaluates {@code suite}'s entry declarations against {@code runtimeData}, in two steps.
     * <ol>
     *   <li><strong>{@code setVars}</strong>: every entry is interpolated, in declaration order, and
     *       written to {@code vars} with {@link RuntimeData#setVar}. An entry that fails is recorded with
     *       {@link RuntimeData#setVarFailed}, leaving a tombstone, and <strong>the remaining entries are
     *       still evaluated</strong> — every key the suite declares ends up either set or tombstoned.
     *       The values resolve against whatever the namespaces hold at this moment; no response is in
     *       scope at suite entry, so a {@code ${response.…}} token fails like any unresolvable one.</li>
     *   <li><strong>{@code skipCondition}</strong>, only if every entry succeeded: interpolated and
     *       resolved.</li>
     * </ol>
     * Tombstones are stamped with {@code runtimeData}'s current path, which the caller keeps at this
     * suite's path.
     *
     * @param suite the suite being entered; must not be {@code null}
     * @param runtimeData the run's namespaces; its {@code vars} gain every entry that succeeded and a
     *        tombstone for every one that failed
     * @return {@link Optional#empty()} to walk into the suite. Otherwise one of two statuses, and never
     *         another:
     *         <ul>
     *           <li>{@link SuiteStatus.Aborted} if any {@code setVars} entry failed — its reason names
     *               every failed key with its cause — or if the skip condition could not be evaluated,
     *               its reason then starting {@code "The skip condition failed"};</li>
     *           <li>{@link SuiteStatus.Skipped} if the skip condition holds, its reason naming the
     *               interpolated condition.</li>
     *         </ul>
     *         A suite declaring neither yields empty.
     * @throws BratException if {@code suite} or {@code runtimeData} is {@code null}
     */
    Optional<SuiteStatus> enter(TestSuite suite, RuntimeData runtimeData) {
        Require.nonNull(suite, "The suite must not be null");
        Require.nonNull(runtimeData, "The runtimeData must not be null");

        var failedVars = new ArrayList<String>();
        for (var setVar : suite.setVars().entrySet()) {
            try {
                var interpolated = interpolation.interpolate(setVar.getValue(), runtimeData);
                runtimeData.setVar(setVar.getKey(), interpolated);
            } catch (Exception e) {
                var message = FailureMessages.causeOf(e, "Computing the var '" + setVar.getKey() + "'");
                runtimeData.setVarFailed(setVar.getKey(), message);
                failedVars.add(message);
            }
        }
        if (!failedVars.isEmpty()) {
            return Optional.of(new SuiteStatus.Aborted(String.join("; ", failedVars)));
        }

        if (suite.skipCondition() != null) {
            try {
                var evaluation = conditionEvaluator.evaluate(suite.skipCondition(), runtimeData);
                if (evaluation.holds()) {
                    return Optional.of(new SuiteStatus.Skipped(
                            String.format("The skip condition of the suite holds: %s", evaluation.condition())));
                }
            } catch (Exception e) {
                return Optional.of(new SuiteStatus.Aborted(FailureMessages.causeOf(e, "The skip condition")));
            }
        }
        return Optional.empty();
    }
}
