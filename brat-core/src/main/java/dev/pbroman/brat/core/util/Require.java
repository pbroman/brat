package dev.pbroman.brat.core.util;

import java.util.Collection;
import java.util.Objects;

import dev.pbroman.brat.core.exception.BratException;
import org.apache.commons.lang3.StringUtils;

/**
 * Argument guards that fail with a {@link BratException}.
 * <p>
 * The message is always supplied by the caller, since a guard has no idea what the value it was
 * handed means to the code that passed it.
 */
public final class Require {

    private Require() {
        // no instances
    }

    /**
     * Requires that {@code value} is not {@code null}.
     *
     * @param value the value to check
     * @param message the message of the exception thrown if it is {@code null}
     * @throws BratException if {@code value} is {@code null}
     */
    public static void nonNull(Object value, String message) {
        if (value == null) {
            throw new BratException(message);
        }
    }

    /**
     * Requires that {@code value} is neither {@code null} nor blank.
     * <p>
     * Blank is {@link StringUtils#isBlank}'s: {@code null}, empty, or whitespace only. A value whose
     * only content is whitespace is rejected rather than trimmed — this decides whether a value is
     * usable, and does not alter it.
     *
     * @param value the value to check
     * @param message the message of the exception thrown if it is {@code null} or blank
     * @throws BratException if {@code value} is {@code null}, empty, or whitespace only
     */
    public static void nonBlank(String value, String message) {
        if (StringUtils.isBlank(value)) {
            throw new BratException(message);
        }
    }

    /**
     * Requires that {@code collection} is neither {@code null} nor holds a {@code null} element.
     *
     * @param collection the collection to check
     * @param message the message of the exception thrown if the collection or an element is {@code null}
     * @throws BratException if {@code collection} is {@code null} or holds a {@code null} element
     */
    public static void noNullElements(Collection<?> collection, String message) {
        nonNull(collection, message);
        if (collection.stream().anyMatch(Objects::isNull)) {
            throw new BratException(message);
        }
    }
}
