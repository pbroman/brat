package dev.pbroman.brat.core.util;

import dev.pbroman.brat.core.exception.BratException;

/**
 * Static helpers for reading a duration an author wrote as text.
 */
public final class DurationUtils {

    private DurationUtils() {
        // static helpers only
    }

    /**
     * Reads a pause the author declared, as a whole number of milliseconds that may be zero.
     *
     * @param declared the authored text, or {@code null} where nothing was declared; surrounding
     *        whitespace is ignored
     * @param field the authored key the text came from, named in any error
     * @return the milliseconds; {@code 0} when {@code declared} is {@code null}, and never negative
     * @throws BratException if {@code declared} is not a whole number — blank included — naming
     *         {@code field} and quoting the value
     * @throws BratException if {@code declared} is negative, naming {@code field} and quoting the value
     */
    public static long nonNegativeMillis(String declared, String field) {
        if (declared == null) {
            return 0;
        }
        long parsed;
        try {
            parsed = Long.parseLong(declared.trim());
        } catch (NumberFormatException e) {
            throw new BratException("The " + field + " '" + declared + "' is not a whole number of milliseconds", e);
        }
        if (parsed < 0) {
            throw new BratException("The " + field + " '" + declared + "' must not be negative");
        }
        return parsed;
    }
}
