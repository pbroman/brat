package dev.pbroman.brat.core.util;

import dev.pbroman.brat.core.exception.BratException;

import java.util.List;
import java.util.Objects;

/**
 * Utilities for exceptions.
 */
public class ExceptionUtils {

    private ExceptionUtils() {
        // no instances
    }

    /**
     * Throws a {@link BratException} if the value is null.
     * @param value the value to check
     * @param message a message for the exception
     */
    public static void bratExceptionOnNull(Object value, String message) {
        if (value == null) {
            throw new BratException(message);
        }
    }

    /**
     * Throws a {@link BratException} if a list or any value in the list is null.
     * @param list the list to check
     * @param message a message for the exception
     */
    public static void bratExceptionOnAnyNull(List<?> list, String message) {
        bratExceptionOnNull(list, message);
        if (list.stream().anyMatch(Objects::isNull)) {
            throw new BratException(message);
        }
    }

}
