package dev.pbroman.brat.core.resolver.condition.rules;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.api.util.ArgsUtils;
import dev.pbroman.brat.core.exception.BratException;
import org.apache.commons.lang3.StringUtils;

import static dev.pbroman.brat.core.util.Constants.ARG_MAX;
import static dev.pbroman.brat.core.util.Constants.ARG_MIN;

/**
 * Static helpers that check and read condition operands, shared by the core rules so each check has
 * one wording.
 */
final class OperandUtils {

    private OperandUtils() {}

    /**
     * Throws unless {@code a} can be read as text, naming the func and which kind of structure
     * {@code a} was — never its content, which may hold a secret.
     *
     * @param a the subject of the condition
     * @param subject the func as a message names it, such as {@code the String func 'startsWith'}
     * @throws BratException if {@code a} is a {@link List} ("a sequence") or a {@link Map} ("a
     *         mapping"); the message starts with {@code subject}, capitalised
     */
    static void requireText(Object a, String subject) {
        if (a instanceof List || a instanceof Map) {
            var kind = a instanceof List ? "a sequence" : "a mapping";
            throw new BratException(StringUtils.capitalize(subject) + " needs text, but a was " + kind
                    + "; a JSON array or object is checked with the structural funcs");
        }
    }

    /**
     * Reads a size given as an operand or an argument, written as a YAML-native number or as text.
     *
     * @param value the size; may be {@code null}
     * @return the size
     * @throws BratException if {@code value} is not a whole number — {@code null}, {@code "abc"} and
     *         {@code 2.5} included; a trailing zero fraction ({@code 3.0}) is a whole number
     */
    static int size(Object value) {
        try {
            return new BigDecimal(String.valueOf(value).trim()).intValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            throw new BratException("A size must be a whole number, but was '" + value + "'");
        }
    }

    /**
     * Whether {@code size} lies between the {@code min} and {@code max} arguments, both inclusive.
     *
     * @param size the size to check
     * @param args the func's arguments; must not be {@code null}
     * @param subject the func as a message names it
     * @return {@code true} if {@code min <= size <= max}
     * @throws BratException if {@code args} holds a key other than {@code min} and {@code max}, lacks
     *         either of them, or either is not a whole number
     */
    static boolean sizeBetween(int size, Map<String, String> args, String subject) {
        ArgsUtils.rejectUnknownArgs(args, subject, ARG_MIN, ARG_MAX);
        return size >= size(ArgsUtils.requiredArg(args, subject, ARG_MIN))
                && size <= size(ArgsUtils.requiredArg(args, subject, ARG_MAX));
    }
}
