package dev.pbroman.brat.core.resolver.assertion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import dev.pbroman.brat.core.api.interpolation.ConfigDataInterpolator;
import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.api.resolver.AssertionResolver;
import dev.pbroman.brat.core.api.resolver.ConditionResolver;
import dev.pbroman.brat.core.data.Assertion;
import dev.pbroman.brat.core.data.AssertionSeverity;
import dev.pbroman.brat.core.data.ChainedCondition;
import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.data.result.AssertionResult;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;

import static dev.pbroman.brat.core.interpolation.configdata.InterpolatorUtils.checkNotInterpolated;

/**
 * Resolves an {@link Assertion} and every {@link ChainedCondition} chained
 * onto it, yielding one {@link AssertionResult} per link.
 * <p>
 * The assertion is interpolated <em>once</em>, as an assertion, before anything is resolved. Each
 * chain link then becomes a full {@link Condition} by borrowing the already-interpolated {@code a} —
 * so a chain over a non-deterministic {@code a} such as {@code ${__uuid()}} tests one value several
 * ways, which is what a chain is for.
 */
public final class AssertionChainResolver implements AssertionResolver {

    private final Interpolation interpolation;
    private final ConditionResolver conditionResolver;
    private final ConfigDataInterpolator<Assertion> assertionInterpolator;

    /**
     * Constructs a resolver over the collaborators it delegates to.
     *
     * @param interpolation the interpolation handed to {@code assertionInterpolator}
     * @param conditionResolver the resolver each interpolated condition is tested with
     * @param assertionInterpolator the interpolator producing the interpolated copy of an assertion,
     *        chain included
     */
    public AssertionChainResolver(
            Interpolation interpolation,
            ConditionResolver conditionResolver,
            ConfigDataInterpolator<Assertion> assertionInterpolator) {
        this.interpolation = interpolation;
        this.conditionResolver = conditionResolver;
        this.assertionInterpolator = assertionInterpolator;
    }

    /**
     * Resolves the assertion and each of its chained conditions.
     * <p>
     * The assertion is interpolated once; the first result is the assertion's own condition, followed
     * by one result per chain link in declaration order. Every result carries the assertion's
     * severity. A link reports its own message where it declares one, and the assertion's otherwise.
     * <p>
     * A failure to interpolate becomes data rather than an escaping exception: the returned list then
     * holds a <em>single</em> failed result naming the interpolation error, because the assertion is
     * interpolated as a unit and a chain is interpolated wholly or not at all.
     *
     * @param assertion the assertion to resolve, as authored — never an interpolated copy
     * @param runtimeData the object containing values
     * @return one result per condition — the assertion's own plus one per chain link, in that order
     *         — or a single failed result if the assertion could not be interpolated
     * @throws dev.pbroman.brat.core.exception.BratException if {@code assertion} is already an
     *         interpolated copy. That is a wiring error rather than an authoring one, so it is not
     *         converted to a result
     */
    @Override
    public List<AssertionResult> resolve(Assertion assertion, RuntimeData runtimeData) {
        checkNotInterpolated(assertion);

        Assertion interpolated;
        try {
            interpolated = assertionInterpolator.interpolated(assertion, interpolation, runtimeData);
        } catch (BratException e) {
            var failMessage = String.format(
                    "Error interpolating assertion: %s, message: %s", assertion.getMessage(), e.getMessage());
            return List.of(new AssertionResult(assertion, failMessage, false, assertion.getSeverity()));
        }

        var results = new ArrayList<AssertionResult>();
        results.add(result(interpolated, interpolated.getMessage(), interpolated.getSeverity()));
        for (var link : interpolated.getChain()) {
            var message = link.getMessage() != null ? link.getMessage() : interpolated.getMessage();
            results.add(result(linkCondition(interpolated, link), message, interpolated.getSeverity()));
        }
        return results;
    }

    /**
     * Resolves one condition and pairs the verdict with the message and severity to report it under.
     *
     * @param condition the interpolated condition to resolve
     * @param message the message to report if it fails
     * @param severity the severity to record on the result
     * @return the result of resolving {@code condition}
     */
    private AssertionResult result(Condition condition, String message, AssertionSeverity severity) {
        return new AssertionResult(condition, message, conditionResolver.resolve(condition), severity);
    }

    /**
     * Builds the full condition for one chain link from an already-interpolated assertion.
     * <p>
     * The link contributes {@code func}, {@code b} and {@code args}; the assertion contributes
     * {@code a}. The outcomes compose the same way, and the key sets cannot collide — a link records
     * only {@code b}, {@code args.*} and {@code message}, never an {@code a} — so the returned
     * condition carries the substitutions of both and a report loses nothing.
     * <p>
     * Both arguments are interpolated copies, so neither outcome map can be {@code null}; only the
     * {@code a} entry is optional, absent when the assertion's own {@code a} was {@code null}.
     *
     * @param assertion the interpolated assertion the link hangs off
     * @param link the interpolated chain link
     * @return an interpolated condition combining the two, whose {@code getOutcomes()} holds the
     *         assertion's {@code a} outcome, where it recorded one, plus every outcome the link
     *         recorded
     */
    private Condition linkCondition(Assertion assertion, ChainedCondition link) {
        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();
        var aOutcome = assertion.getOutcomes().get("a");
        if (aOutcome != null) {
            outcomes.put("a", aOutcome);
        }
        outcomes.putAll(link.getOutcomes());
        return new Condition(link.getFunc(), assertion.getA(), link.getB(), link.getArgs(), outcomes);
    }
}
