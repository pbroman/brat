package dev.pbroman.brat.core.util;

import java.util.Arrays;
import java.util.Map;

import dev.pbroman.brat.core.exception.BratException;

/**
 * Guards for an {@code args} bag — the map of extra arguments that sits beside a discriminator.
 * <p>
 * <strong>The price of a bag is that its owner validates it.</strong> A typed field makes
 * {@code ofset: 0.01} fail when the document binds; a bag swallows it, and an unread key never
 * reaches a point of use, so the failure an author needs has to be raised deliberately. Whoever reads
 * the discriminator — a condition rule reading {@code func}, a request handler reading its own name —
 * knows the legal key set and is the only thing that can.
 * <p>
 * The <strong>subject</strong> is a parameter because these guards serve unrelated owners: a message
 * reading "this function" is wrong in a handler, and hardcoding it is what kept these methods locked
 * inside the condition rules until a handler needed them too.
 */
public final class ArgsUtils {

    private ArgsUtils() {
        // utility class
    }

    /**
     * Rejects any argument its owner does not know, so a typo fails loudly rather than being ignored.
     *
     * @param args the bag to check; never {@code null}, and an empty one passes whatever is legal
     * @param subject what owns the bag, named as it should read in the message — for example
     *        {@code the 'httpclient5' request handler} or {@code the Number func 'isBetween'}
     * @param legalKeys every key the owner accepts; none means the owner takes no arguments at all,
     *        and any key is then unknown
     * @throws BratException if {@code args} holds a key that is not among {@code legalKeys}, naming
     *         the offending keys in a stable order, the subject, and what it does accept — or that it
     *         accepts nothing, which is a different thing to tell an author than an empty list. Never
     *         the values, which may hold a secret
     */
    public static void rejectUnknownArgs(Map<String, String> args, String subject, String... legalKeys) {
        var legalKeysList = Arrays.asList(legalKeys);
        // Sorted, so two runs of the same suite report the same message: an args bag may be any Map,
        // and a HashMap's iteration order is not the author's. The legal keys keep the order the
        // caller declared them in, which is the order its own documentation lists them in.
        var illegalKeys = args.keySet().stream()
                .filter(key -> !legalKeysList.contains(key))
                .sorted()
                .toList();
        if (!illegalKeys.isEmpty()) {
            throw new BratException(
                    legalKeysList.isEmpty()
                            ? String.format("The args %s are unknown to %s, which takes no args", illegalKeys, subject)
                            : String.format(
                                    "The args %s are unknown to %s. Known args: %s",
                                    illegalKeys, subject, legalKeysList));
        }
    }

    /**
     * Returns an argument its owner requires.
     *
     * @param args the bag to read; never {@code null}
     * @param subject what owns the bag, named as it should read in the message
     * @param key the argument to read
     * @return the value, which is never {@code null}
     * @throws BratException if {@code args} has no entry for {@code key}, or holds it as {@code null}
     */
    public static String requiredArg(Map<String, String> args, String subject, String key) {
        var argument = args.get(key);
        if (argument == null) {
            throw new BratException(String.format("No '%s' argument was declared, which %s requires", key, subject));
        }
        return argument;
    }
}
