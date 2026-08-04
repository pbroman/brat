package dev.pbroman.brat.core.util;

import java.util.List;
import java.util.Objects;

import dev.pbroman.brat.core.exception.BratException;

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
     * Requires that {@code list} is neither {@code null} nor holds a {@code null} element.
     *
     * @param list the list to check
     * @param message the message of the exception thrown if the list or an element is {@code null}
     * @throws BratException if {@code list} is {@code null} or holds a {@code null} element
     */
    public static void noNullElements(List<?> list, String message) {
        nonNull(list, message);
        if (list.stream().anyMatch(Objects::isNull)) {
            throw new BratException(message);
        }
    }
}
