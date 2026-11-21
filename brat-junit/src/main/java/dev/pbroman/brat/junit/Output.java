package dev.pbroman.brat.junit;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import dev.pbroman.brat.core.api.reporting.ReportDetail;
import dev.pbroman.brat.core.exception.BratException;

/**
 * What a run's tests print while they execute, and the detail a failed request's message carries.
 *
 * @param quiet whether the tests print nothing at all
 * @param detail what is shown beneath a request — on the console, and in a failure's message
 */
record Output(boolean quiet, ReportDetail detail) {

    /** The prefix of a configuration key choosing a detail: {@code brat.show.response=all}. */
    static final String SHOW_PREFIX = "brat.show.";

    /** The configuration key silencing the tests: {@code brat.quiet=true}. */
    static final String QUIET = "brat.quiet";

    /** The file JUnit reads its configuration from, on the test classpath. */
    static final String PROPERTIES_FILE = "junit-platform.properties";

    /**
     * Resolves what the tests print, one choice at a time, the first that sets it winning: the test's
     * own {@code shown}/{@code quiet}, then {@code system}, then {@code file}, then the default — every
     * detail at {@code brat run}'s default, and not quiet.
     * <p>
     * A detail is configured as {@code brat.show.<key>} with a key and a value {@code brat run}'s
     * {@code --show-*} options take ({@code response}, {@code request}, {@code assertions},
     * {@code secrets}); quiet as {@code brat.quiet} with {@code true} or {@code false}, in any case.
     * Other configuration keys are ignored.
     *
     * @param shown the test's own choices, by detail key; possibly empty, never {@code null}
     * @param quiet the test's own choice, or {@code null} if it made none
     * @param system the system properties; never {@code null}
     * @param file the content of {@code junit-platform.properties}, empty without one; never {@code null}
     * @return the output
     * @throws BratException for a {@code brat.show.} key naming no detail, naming the details there are;
     *         for a {@code brat.quiet} that is neither {@code true} nor {@code false}; or for a value a
     *         detail does not take — each naming where it was found
     */
    static Output resolve(Map<String, String> shown, Boolean quiet, Properties system, Properties file) {
        var args = new LinkedHashMap<String, String>();
        // lowest first, so each later source overwrites what an earlier one set
        readShow(file, PROPERTIES_FILE, args);
        readShow(system, "a system property", args);
        args.putAll(shown);
        ReportDetail detail;
        try {
            detail = ReportDetail.of(args);
        } catch (BratException e) {
            throw new BratException(
                    "What the tests show is configured wrongly (" + String.join(", ", configured(args.keySet())) + "): "
                            + e.getMessage(),
                    e);
        }
        var silent = quiet != null
                ? quiet
                : readQuiet(system, "a system property")
                        .or(() -> readQuiet(file, PROPERTIES_FILE))
                        .orElse(false);
        return new Output(silent, detail);
    }

    private static void readShow(Properties properties, String where, Map<String, String> args) {
        for (var name : properties.stringPropertyNames()) {
            if (!name.startsWith(SHOW_PREFIX)) {
                continue;
            }
            var key = name.substring(SHOW_PREFIX.length());
            if (!ReportDetail.KEYS.contains(key)) {
                throw new BratException("'" + name + "' in " + where + " names no detail: the details are "
                        + ReportDetail.KEYS + ", as brat.show." + ReportDetail.KEYS.getFirst());
            }
            args.put(key, properties.getProperty(name));
        }
    }

    /** The {@code brat.quiet} {@code properties} configure, if they configure one. */
    private static Optional<Boolean> readQuiet(Properties properties, String where) {
        var value = properties.getProperty(QUIET);
        if (value == null) {
            return Optional.empty();
        }
        if (value.strip().equalsIgnoreCase("true")) {
            return Optional.of(true);
        }
        if (value.strip().equalsIgnoreCase("false")) {
            return Optional.of(false);
        }
        throw new BratException("'" + QUIET + "=" + value + "' in " + where + " is neither true nor false");
    }

    private static List<String> configured(Iterable<String> keys) {
        var names = new ArrayList<String>();
        for (var key : keys) {
            names.add(SHOW_PREFIX + key);
        }
        return names;
    }

    /**
     * Reads {@code junit-platform.properties} from {@code loader}, as JUnit itself does.
     *
     * @param loader the class loader the test classpath is reached through; never {@code null}
     * @return the file's properties, empty if there is no such file
     * @throws BratException if the file exists but cannot be read, naming it
     */
    static Properties junitPlatformProperties(ClassLoader loader) {
        var properties = new Properties();
        try (InputStream in = loader.getResourceAsStream(PROPERTIES_FILE)) {
            if (in != null) {
                properties.load(in);
            }
        } catch (IOException | IllegalArgumentException e) {
            throw new BratException("Could not read " + PROPERTIES_FILE + ": " + e.getMessage(), e);
        }
        return properties;
    }
}
