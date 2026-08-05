package dev.pbroman.brat.core.api.interpolation;

import java.math.BigDecimal;
import java.util.List;

import dev.pbroman.brat.core.exception.BratException;

/**
 * Argument guards for writing a {@link BratFunction}.
 * <p>
 * A function receives a plain {@code List<String>} and <strong>nothing validates it on the way
 * in</strong> — not the count, not the contents. Each function checks its own, and these are the
 * checks that would otherwise be written out by hand in every one. They are as useful to a plugin as
 * to the functions {@code brat-core} ships with, and using them keeps the message an author sees
 * consistent whoever wrote the function.
 * <p>
 * Every one fails with a {@link BratException} naming the function as an author spelled it, prefix
 * included.
 */
public final class FunctionArgs {

    private FunctionArgs() {
        // no instances
    }

    /**
     * Requires an argument count within {@code min..max} inclusive.
     *
     * @param name the function's bare name, used in the message
     * @param args the arguments as received
     * @param min the fewest arguments accepted
     * @param max the most arguments accepted
     * @throws BratException if the count is outside the range
     */
    public static void requireArgCount(String name, List<String> args, int min, int max) {
        if (args.size() < min || args.size() > max) {
            var expected = min == max ? "exactly " + min : min + " to " + max;
            throw new BratException("__" + name + " takes " + expected + " argument(s), but got " + args.size());
        }
    }

    /**
     * Parses an argument as a decimal number.
     *
     * @param name the function's bare name, used in the message
     * @param value the argument
     * @return the parsed number
     * @throws BratException if {@code value} is not a number
     */
    public static BigDecimal decimal(String name, String value) {
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            throw new BratException("__" + name + " needs a number, but got '" + value + "'", e);
        }
    }

    /**
     * Parses an argument as a whole number.
     *
     * @param name the function's bare name, used in the message
     * @param value the argument
     * @return the parsed number
     * @throws BratException if {@code value} is not a whole number
     */
    public static long integer(String name, String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new BratException("__" + name + " needs a whole number, but got '" + value + "'", e);
        }
    }

    /**
     * Parses an argument as a whole number that must fall within a range, and narrows it to an
     * {@code int}.
     * <p>
     * Use this rather than casting {@link #integer(String, String)} yourself wherever the value is an
     * index, a length or a count. The cast is the point: narrowing a {@code long} silently wraps, so
     * a length of {@code 4294967296} would become {@code 0} and a suite would get an empty string
     * where it asked for a value. Bounding first makes the narrowing safe.
     *
     * @param name the function's bare name, used in the message
     * @param value the argument
     * @param min the smallest value accepted, inclusive
     * @param max the largest value accepted, inclusive
     * @return the parsed number, guaranteed to be within {@code min..max}
     * @throws BratException if {@code value} is not a whole number, or falls outside the range
     */
    public static int integerWithin(String name, String value, int min, int max) {
        var parsed = integer(name, value);
        if (parsed < min || parsed > max) {
            throw new BratException(
                    "__" + name + " needs a whole number between " + min + " and " + max + ", but got '" + value + "'");
        }
        return (int) parsed;
    }

    /**
     * Requires that {@code min} is not above {@code max}.
     *
     * @param name the function's bare name, used in the message
     * @param min the lower bound
     * @param max the upper bound
     * @throws BratException if {@code min} is greater than {@code max}
     */
    public static void requireRange(String name, long min, long max) {
        if (min > max) {
            throw new BratException(
                    "__" + name + " needs a lower bound at or below the upper, but got " + min + " and " + max);
        }
    }
}
