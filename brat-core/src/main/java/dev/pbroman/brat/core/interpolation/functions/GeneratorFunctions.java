package dev.pbroman.brat.core.interpolation.functions;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import dev.pbroman.brat.core.api.interpolation.BratFunction;
import dev.pbroman.brat.core.exception.BratException;

import static dev.pbroman.brat.core.api.interpolation.FunctionArgs.decimal;
import static dev.pbroman.brat.core.api.interpolation.FunctionArgs.integer;
import static dev.pbroman.brat.core.api.interpolation.FunctionArgs.integerWithin;
import static dev.pbroman.brat.core.api.interpolation.FunctionArgs.requireArgCount;
import static dev.pbroman.brat.core.api.interpolation.FunctionArgs.requireRange;

/**
 * Functions that invent a value: identifiers and random data.
 * <p>
 * <strong>Every one produces a new value on every reference</strong>, deliberately and with no seed —
 * a generator generates. To use the same value twice, capture it once with {@code setVars}.
 *
 * <dl>
 *   <dt>{@code ${__uuid}}</dt>
 *   <dd>A random (version 4) UUID in canonical lower-case form. No arguments.</dd>
 *   <dt>{@code ${__randomInt(min, max)}}</dt>
 *   <dd>A whole number in {@code min..max}, <strong>both bounds inclusive</strong>.</dd>
 *   <dt>{@code ${__randomLong(min, max)}}</dt>
 *   <dd>The same over the whole {@code long} range. {@code max} must be below {@code Long.MAX_VALUE}.</dd>
 *   <dt>{@code ${__randomFloat(min, max)}}</dt>
 *   <dd>A decimal number in {@code min..max}, lower bound inclusive.</dd>
 *   <dt>{@code ${__randomFrom(a, b, …)}}</dt>
 *   <dd>One of the arguments, chosen at random. At least one argument.</dd>
 *   <dt>{@code ${__randomString(length)}} and {@code ${__randomString(length, chars)}}</dt>
 *   <dd>A string of {@code length} characters, drawn from {@code chars} or from
 *       {@code A-Za-z0-9} if no alphabet is given.</dd>
 * </dl>
 */
public final class GeneratorFunctions {

    private static final String DEFAULT_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    /**
     * The longest string {@code __randomString} will build. A cap rather than the {@code int} range,
     * because a test value asking for more than this is a typo, and honouring it would allocate
     * gigabytes before anything noticed.
     */
    private static final int MAX_LENGTH = 1_000_000;

    private GeneratorFunctions() {
        // no instances
    }

    /**
     * The generator functions, keyed by their bare names.
     *
     * @return the functions
     */
    public static Map<String, BratFunction> functions() {
        return Map.of(
                "uuid", GeneratorFunctions::uuid,
                "randomInt", GeneratorFunctions::randomInt,
                "randomLong", GeneratorFunctions::randomLong,
                "randomFloat", GeneratorFunctions::randomFloat,
                "randomFrom", GeneratorFunctions::randomFrom,
                "randomString", GeneratorFunctions::randomString);
    }

    private static String uuid(List<String> args) {
        requireArgCount("uuid", args, 0, 0);
        return UUID.randomUUID().toString();
    }

    private static String randomInt(List<String> args) {
        requireArgCount("randomInt", args, 2, 2);
        return String.valueOf(randomInRange("randomInt", args));
    }

    private static String randomLong(List<String> args) {
        requireArgCount("randomLong", args, 2, 2);
        return String.valueOf(randomInRange("randomLong", args));
    }

    private static long randomInRange(String name, List<String> args) {
        var min = integer(name, args.get(0));
        var max = integer(name, args.get(1));
        requireRange(name, min, max);
        if (max == Long.MAX_VALUE) {
            throw new BratException(
                    "__" + name + " needs an upper bound below " + Long.MAX_VALUE + ", since the bound is inclusive");
        }
        return ThreadLocalRandom.current().nextLong(min, max + 1);
    }

    private static String randomFloat(List<String> args) {
        requireArgCount("randomFloat", args, 2, 2);
        var min = finiteBound(args.get(0));
        var max = finiteBound(args.get(1));
        if (min > max) {
            throw new BratException(
                    "__randomFloat needs a lower bound at or below the upper, but got " + min + " and " + max);
        }
        return String.valueOf(min == max ? min : ThreadLocalRandom.current().nextDouble(min, max));
    }

    /**
     * A bound as a {@code double}, rejecting one too large to be represented — {@code doubleValue}
     * saturates to infinity rather than failing, and an infinite bound blows up inside the generator.
     */
    private static double finiteBound(String value) {
        var bound = decimal("randomFloat", value).doubleValue();
        if (!Double.isFinite(bound)) {
            throw new BratException("__randomFloat needs bounds a decimal number can hold, but got '" + value + "'");
        }
        return bound;
    }

    private static String randomFrom(List<String> args) {
        requireArgCount("randomFrom", args, 1, Integer.MAX_VALUE);
        return args.get(ThreadLocalRandom.current().nextInt(args.size()));
    }

    private static String randomString(List<String> args) {
        requireArgCount("randomString", args, 1, 2);
        var length = integerWithin("randomString", args.getFirst(), 0, MAX_LENGTH);
        var alphabet = args.size() == 2 ? args.get(1) : DEFAULT_ALPHABET;
        if (alphabet.isEmpty()) {
            throw new BratException("__randomString needs a non-empty alphabet to draw from");
        }
        var random = ThreadLocalRandom.current();
        var result = new StringBuilder(length);
        for (var i = 0; i < length; i++) {
            result.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return result.toString();
    }
}
