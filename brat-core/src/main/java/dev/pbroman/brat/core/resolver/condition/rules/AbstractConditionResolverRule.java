package dev.pbroman.brat.core.resolver.condition.rules;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import dev.pbroman.brat.core.api.resolver.ConditionPredicate;
import dev.pbroman.brat.core.api.resolver.ConditionResolverRule;
import dev.pbroman.brat.core.api.util.ArgsUtils;
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

    /** The canonical name of each func, keyed by its normalised form. */
    private final Map<String, String> canonicalNames;

    /**
     * Constructor receiving a predicate map from extending classes.
     * <p>
     * Each key is a func's <strong>canonical spelling</strong>, the one a suite author is offered:
     * {@code isEqualTo}, {@code containsKey}, {@code >=}. The rule then answers to every spelling that
     * normalises like it (see {@link ConditionResolverRule#resolve}), and its own code — the
     * predicates, {@link #accepts}, {@link #ignoreBNullCheck()}, its messages — only ever sees the
     * canonical one.
     *
     * @param predicates the predicate for each func this rule answers to, keyed by its canonical
     *        spelling; must not be {@code null}, nor hold a {@code null} key or predicate
     * @throws BratException if two keys normalise to the same func ({@code isEqualTo} and
     *         {@code equalTo}), so one of them could never be reached; or if a key normalises to a
     *         negation ({@code notarize}, read as {@code arize} negated), so the func it names could
     *         never be reached un-negated
     */
    protected AbstractConditionResolverRule(Map<String, ConditionPredicate> predicates) {
        // Checked while the arguments are evaluated, before any object exists, so a rejected map never
        // leaves a partly constructed rule behind.
        this(predicates, canonicalNames(predicates));
    }

    private AbstractConditionResolverRule(
            Map<String, ConditionPredicate> predicates, Map<String, String> canonicalNames) {
        this.predicateMap = Map.copyOf(predicates);
        this.canonicalNames = canonicalNames;
    }

    /** Maps each key's normalised form to the key, rejecting a key no spelling could reach. */
    private static Map<String, String> canonicalNames(Map<String, ConditionPredicate> predicates) {
        var names = new HashMap<String, String>();
        for (var func : predicates.keySet()) {
            var prepared = prepare(func);
            if (prepared.negate()) {
                throw new BratException("The func '" + func + "' reads as a negation of '" + prepared.function()
                        + "', so it could never be reached un-negated. Name it without a leading 'not' or '!'");
            }
            var clash = names.put(prepared.function(), func);
            if (clash != null) {
                throw new BratException("The funcs '" + clash + "' and '" + func
                        + "' are the same func spelled two ways, so one of them could never be reached");
            }
        }
        return Map.copyOf(names);
    }

    /**
     * Returns the canonical spelling of every func this rule answers to — the keys its constructor was
     * given, which is what an editor offers for completion.
     * <p>
     * The rule may still decline one of these for operands outside its category: being listed means
     * the rule knows the name, not that it answers every condition using it. Negated spellings are not
     * listed, since every func can be negated.
     *
     * @return the canonical func names, unmodifiable, in no particular order; never {@code null}
     */
    public Set<String> funcs() {
        return predicateMap.keySet();
    }

    @Override
    public Optional<Boolean> resolve(Condition condition) {
        nonNull(condition, "The condition may not be null");
        var prepared = prepare(condition.getFunc());
        var func = canonicalNames.get(prepared.function());
        if (func != null) {
            if (!accepts(condition, func)) {
                return Optional.empty();
            }
            nullCheckB(condition, func);
            try {
                return Optional.of(prepared.negate()
                        != predicateMap.get(func).test(condition.getA(), condition.getB(), condition.getArgs()));
            } catch (BratException be) {
                throw be;
            } catch (RuntimeException re) {
                throw new BratException(String.format("Unable to resolve condition %s", condition), re);
            }
        }
        return Optional.empty();
    }

    /**
     * The operand category this rule serves, such as {@code Number} or {@code String}, named in its
     * error messages.
     *
     * @return the category's name; never {@code null}
     */
    protected abstract String category();

    /**
     * Override this to return a list of functions that do not require the b argument.
     *
     * @return the canonical names of the funcs skipped by the null check
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
     * @param function the func's canonical spelling, as this rule keyed its predicate — whatever
     *         spelling or negation the author wrote — so a rule can accept some of its funcs for
     *         operands it declines for others
     * @return {@code true} if this rule should resolve {@code condition}, {@code false} to decline
     *         it and let the dispatcher try the next rule
     */
    protected boolean accepts(Condition condition, String function) {
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
    private static PreparedFunction prepare(String func) {
        var f = stripIsPrefix(func.toLowerCase().trim());
        var matches = NEGATION_PATTERN.matcher(f);
        boolean negate = false;
        if (matches.find()) {
            f = stripIsPrefix(matches.group(2));
            negate = true;
        }
        return new PreparedFunction(f, negate);
    }

    private static String stripIsPrefix(String func) {
        return Strings.CI.startsWith(func, IS_PREFIX) ? StringUtils.substring(func, IS_PREFIX.length()) : func;
    }

    private void nullCheckB(Condition condition, String function) {
        if (condition.getB() == null && !ignoreBNullCheck().contains(function)) {
            throw new BratException(String.format("b may not be null for %s function '%s'", category(), function));
        }
    }

    /**
     * Rejects any argument {@code func} does not know, naming the func in the message.
     *
     * @param args the func's arguments
     * @param category this rule's category, since a predicate is built statically and cannot ask for
     *        it
     * @param func the func whose arguments these are
     * @param legalKeys every key that func accepts
     * @throws BratException if {@code args} holds a key that is not among {@code legalKeys}
     */
    protected static void rejectUnknownArgs(
            Map<String, String> args, String category, String func, String... legalKeys) {
        ArgsUtils.rejectUnknownArgs(args, subject(category, func), legalKeys);
    }

    /**
     * Returns an argument {@code func} requires, naming the func if it is absent.
     *
     * @param args the func's arguments
     * @param category this rule's category, since a predicate is built statically and cannot ask for
     *        it
     * @param func the func whose arguments these are
     * @param key the argument to read
     * @return the value
     * @throws BratException if {@code args} has no entry for {@code key}
     */
    protected static String requiredArg(Map<String, String> args, String category, String func, String key) {
        return ArgsUtils.requiredArg(args, subject(category, func), key);
    }

    /**
     * Names this rule's func the way a message should read it.
     *
     * @param category the rule's category
     * @param func the func being resolved
     * @return the subject, for example {@code the Number func 'isBetween'}
     */
    protected static String subject(String category, String func) {
        return "the " + category + " func '" + func + "'";
    }

    record PreparedFunction(String function, boolean negate) {}
}
