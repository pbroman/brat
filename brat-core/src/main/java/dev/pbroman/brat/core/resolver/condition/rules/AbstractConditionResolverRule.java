package dev.pbroman.brat.core.resolver.condition.rules;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import dev.pbroman.brat.core.api.resolver.ConditionPredicate;
import dev.pbroman.brat.core.api.resolver.ConditionResolverRule;
import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.exception.BratException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

import static dev.pbroman.brat.core.util.Constants.IS_PREFIX;
import static dev.pbroman.brat.core.util.Constants.NEGATION_PATTERN;
import static dev.pbroman.brat.core.util.Require.nonNull;

/**
 * Abstract implementation of the {@link ConditionResolverRule} providing basic functionality.
 */
public abstract class AbstractConditionResolverRule implements ConditionResolverRule {

    private final Map<String, ConditionPredicate> predicateMap;

    /**
     * Constructor receiving a predicate map from extending classes.
     *
     * @param predicates the predicate for each func this rule answers to, keyed by the normalised
     *        func name
     */
    protected AbstractConditionResolverRule(Map<String, ConditionPredicate> predicates) {
        this.predicateMap = Map.copyOf(predicates);
    }

    @Override
    public Optional<Boolean> resolve(Condition condition) {
        nonNull(condition, "The condition may not be null");
        var prepared = prepare(condition.getFunc());
        if (predicateMap.containsKey(prepared.function())) {
            if (!accepts(condition)) {
                return Optional.empty();
            }
            nullCheckB(condition, prepared.function());
            try {
                return Optional.of(prepared.negate()
                        != predicateMap
                                .get(prepared.function())
                                .test(condition.getA(), condition.getB(), condition.getArgs()));
            } catch (BratException be) {
                throw be;
            } catch (RuntimeException re) {
                throw new BratException(String.format("Unable to resolve condition %s", condition), re);
            }
        }
        return Optional.empty();
    }

    /**
     * Override this to return a list of functions that do not require the b argument.
     * @return a list of function names skipped by the null check
     */
    protected List<String> ignoreBNullCheck() {
        return List.of();
    }

    /**
     * Override this to decline conditions whose operands do not belong to this rule's category, so
     * one func name can serve several categories. The default accepts everything, which is what the
     * fallback rule of a category chain wants.
     *
     * @param condition the condition about to be resolved, its func already matched
     * @return {@code true} if this rule should resolve {@code condition}, {@code false} to decline
     *         it and let the dispatcher try the next rule
     */
    protected boolean accepts(Condition condition) {
        return true;
    }

    /**
     * Normalises a func name: lowercased and trimmed, then a leading {@code not}/{@code !} taken as
     * negation, with an optional {@code is} prefix stripped on either side of it.
     * <p>
     * Stripping {@code is} both before and after the negation is what lets all four spellings reach
     * the same predicate: {@code isNotNull} and {@code notIsNull} negated, {@code isNull} and
     * {@code null} not.
     */
    private PreparedFunction prepare(String func) {
        var f = stripIsPrefix(func.toLowerCase().trim());
        var matches = NEGATION_PATTERN.matcher(f);
        boolean negate = false;
        if (matches.find()) {
            f = stripIsPrefix(matches.group(2));
            negate = true;
        }
        return new PreparedFunction(f, negate);
    }

    private String stripIsPrefix(String func) {
        return Strings.CI.startsWith(func, IS_PREFIX) ? StringUtils.substring(func, IS_PREFIX.length()) : func;
    }

    private void nullCheckB(Condition condition, String function) {
        if (condition.getB() == null && !ignoreBNullCheck().contains(function)) {
            throw new BratException(String.format("b may not be null for %s function '%s'", category(), function));
        }
    }

    /**
     * Rejects any argument this func does not know, so a typo fails loudly instead of being
     * silently ignored — the check a typed field would have given for free.
     *
     * @param args the func's arguments
     * @param legalKeys every key this func accepts
     * @throws BratException if {@code args} holds a key that is not among {@code legalKeys}
     */
    protected static void rejectUnknownArgs(Map<String, String> args, String... legalKeys) {
        var legal = Set.of(legalKeys);
        var unknown = args.keySet().stream()
                .filter(key -> !legal.contains(key))
                .sorted()
                .toList();
        if (!unknown.isEmpty()) {
            throw new BratException("Unknown argument(s) " + unknown + "; this function takes " + legal);
        }
    }

    /**
     * Returns an argument this func requires.
     *
     * @param args the func's arguments
     * @param key the argument to read
     * @return the value
     * @throws BratException if {@code args} has no entry for {@code key}
     */
    protected static String requiredArg(Map<String, String> args, String key) {
        var value = args.get(key);
        if (value == null) {
            throw new BratException("The argument '" + key + "' is required for this function");
        }
        return value;
    }

    record PreparedFunction(String function, boolean negate) {}
}
