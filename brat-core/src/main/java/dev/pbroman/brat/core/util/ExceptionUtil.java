package dev.pbroman.brat.core.util;

import dev.pbroman.brat.core.exception.BratException;

/**
 * Utilities for exceptions.
 */
public class ExceptionUtil {

    private ExceptionUtil() {
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

}
