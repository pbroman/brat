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

import static dev.pbroman.brat.core.util.Constants.IS_NULL;
import static dev.pbroman.brat.core.util.Constants.IS_PREFIX;
import static dev.pbroman.brat.core.util.Constants.NEGATION_PATTERN;
import static dev.pbroman.brat.core.util.Require.nonNull;

/**
 * Abstract implementation of the {@link ConditionResolverRule} providing basic functionality: func
 * names matched in every spelling, negation, the check that {@code b} is given, and the answer for a
 * null {@code a}.
 */
public abstract class AbstractConditionResolverRule implements ConditionResolverRule {

    private final Map<String, ConditionPredicate> predicateMap;

    private static final String IS_NOT_PREFIX = IS_PREFIX + "not";

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

    /**
     * Resolves {@code condition} if its func is one of this rule's keys, in any spelling, and declines
     * it otherwise.
     * <p>
     * <strong>A null {@code a} is only for {@code isNull}</strong>, and is dealt with here, before
     * {@link #accepts} is asked and without calling a predicate, so neither ever sees one:
     * {@code isNull} is {@code true} and {@code isNotNull} {@code false}; every other func of this
     * rule's, negated or not, throws. A func this rule does not know is declined, null {@code a} or not,
     * so an unknown name is never answered here.
     *
     * @param condition the condition to resolve; never {@code null}
     * @return the verdict, or {@link Optional#empty()} if the func is not this rule's, or {@link #accepts}
     *         declines the operands
     * @throws BratException if {@code condition} is {@code null}; if {@code a} is null and the func is
     *         one of this rule's other than {@code isNull} — the message names the func, says {@code a}
     *         is null and points at {@code isNull}/{@code isNotNull}; if {@code a} is not null, the func
     *         needs a {@code b} and none is given; or if the predicate fails
     */
    @Override
    public Optional<Boolean> resolve(Condition condition) {
        nonNull(condition, "The condition may not be null");
        var prepared = prepare(condition.getFunc());
        var func = canonicalNames.getOrDefault(prepared.function(), canonicalNames.get(prepared.unstripped()));
        if (func != null) {
            if (condition.getA() == null) {
                return Optional.of(answerForNull(func, prepared.negate()));
            }
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
     * The answer for a null {@code a}, as {@link #resolve} documents it.
     *
     * @param func the func's canonical name
     * @param negate whether the author negated it
     * @return the verdict, for {@code isNull}
     * @throws BratException for any other func
     */
    private boolean answerForNull(String func, boolean negate) {
        if (IS_NULL.equalsIgnoreCase(func)) {
            return !negate;
        }
        throw new BratException(
                String.format("The 'a' value for the func '%s' is null; check it with isNull or isNotNull", func));
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
     * Normalises a func name: lowercased and trimmed, the negation read off first — a leading
     * {@code isNot} stands for {@code is} negated, a leading {@code not} or {@code !} for what follows
     * it negated — and then one optional {@code is} stripped from what remains.
     * <p>
     * Reading the negation first is what keeps a func whose own name starts with {@code Is} reachable:
     * {@code isNotIsoDateTime} is {@code isIsoDateTime} negated, normalised once to {@code isodatetime}.
     * Its {@code is}-less spellings ({@code IsoDateTime}, {@code notIsoDateTime}) lose an {@code is}
     * that was part of the name, which is why the lookup falls back to the unstripped form.
     */
    private static PreparedFunction prepare(String func) {
        var f = func.toLowerCase().trim();
        var negate = false;
        if (f.startsWith(IS_NOT_PREFIX)) {
            f = IS_PREFIX + f.substring(IS_NOT_PREFIX.length());
            negate = true;
        } else {
            var matches = NEGATION_PATTERN.matcher(f);
            if (matches.find()) {
                f = matches.group(2);
                negate = true;
            }
        }
        return new PreparedFunction(stripIsPrefix(f), f, negate);
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

    /**
     * A func name as the author wrote it, negation read off.
     *
     * @param function the normalised name, one {@code is} stripped — what a key normalises to
     * @param unstripped the same before stripping {@code is}, tried when {@code function} matches no
     *        key, so the {@code is}-less spelling of a name like {@code isIsoDateTime} is reached
     * @param negate whether the author negated it
     */
    record PreparedFunction(String function, String unstripped, boolean negate) {}
}
