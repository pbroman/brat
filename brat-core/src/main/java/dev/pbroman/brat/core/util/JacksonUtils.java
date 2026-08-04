package dev.pbroman.brat.core.util;

import tools.jackson.core.JacksonException;

/**
 * Utilities for JSON/YAML parsing with Jackson.
 */
public final class JacksonUtils {

    private JacksonUtils() {
        // utility class
    }

    /**
     * Returns only the location line and column of a {@link JacksonException}. This is to avoid the exception
     * message exposing potential secrets in the YAML document.
     *
     * @param e - the {@link JacksonException}
     * @return a string with the location
     */
    public static String locationOf(JacksonException e) {
        var location = e.getLocation();
        return location == null ? "" : " at line " + location.getLineNr() + ", column " + location.getColumnNr();
    }
}
