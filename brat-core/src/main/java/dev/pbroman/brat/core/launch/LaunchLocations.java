package dev.pbroman.brat.core.launch;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.util.Require;

import static dev.pbroman.brat.core.util.Constants.CLASSPATH_PREFIX;
import static dev.pbroman.brat.core.util.Constants.FILE_PREFIX;

/**
 * Converts the locations given at launch — the suite file, the environment directory — into the
 * prefixed, absolute form the rest of BRAT reads.
 * <p>
 * The two disagree about a path with no prefix. To whoever launches a run, {@code orders.brat.yaml}
 * is a file next to where they are; to {@link dev.pbroman.brat.core.util.ResourceReader} it is a
 * classpath lookup. So every location a launch is given passes through here, and nothing downstream
 * ever sees the string as given.
 */
final class LaunchLocations {

    private LaunchLocations() {
        // utility class
    }

    /**
     * Returns the location of a file as {@code ResourceReader} reads it: prefixed and, on the
     * filesystem, absolute.
     * <ul>
     *     <li>{@code classpath:…} — returned unchanged.</li>
     *     <li>{@code file:…} — the rest is a filesystem path, handled as below.</li>
     *     <li>anything else — a filesystem path, relative to the working directory unless absolute.</li>
     * </ul>
     * A filesystem path is made absolute and normalised ({@code a/../b} becomes {@code b}) and
     * returned with a {@code file:} prefix. Nothing is checked against the filesystem: the file need
     * not exist. A {@code ~} is not expanded — that is the shell's job.
     *
     * @param given the location as given at launch; must not be {@code null} or blank
     * @return {@code classpath:…} unchanged, otherwise {@code file:} followed by an absolute,
     *         normalised path
     * @throws BratException if {@code given} is {@code null} or blank, or names no valid path —
     *         {@code file:} with nothing after it included
     */
    static String fileLocation(String given) {
        Require.nonBlank(given, "A location given at launch must not be blank");
        if (given.startsWith(CLASSPATH_PREFIX)) {
            return given;
        }
        return FILE_PREFIX + filesystemPath(given);
    }

    /**
     * Returns a directory as a filesystem path, absolute and normalised.
     * <p>
     * The forms are those of {@link #fileLocation(String)} except {@code classpath:}, which is
     * rejected: a classpath directory cannot be listed reliably, and listing is what a directory is
     * read for. Nothing is checked against the filesystem.
     *
     * @param given the directory as given at launch; must not be {@code null} or blank
     * @return the directory as an absolute, normalised path
     * @throws BratException if {@code given} is {@code null} or blank, names no valid path, or
     *         carries a {@code classpath:} prefix — the message saying a directory must be on the
     *         filesystem
     */
    static Path directory(String given) {
        Require.nonBlank(given, "A directory given at launch must not be blank");
        if (given.startsWith(CLASSPATH_PREFIX)) {
            throw new BratException("The directory '" + given + "' is on the classpath, but a directory must be on "
                    + "the filesystem: a classpath directory cannot be listed reliably");
        }
        return filesystemPath(given);
    }

    /**
     * Reads {@code given}, with or without a {@code file:} prefix, as an absolute, normalised path.
     *
     * @param given a location that is not on the classpath
     * @return the path
     * @throws BratException if no path remains once the prefix is removed, or the rest is not a
     *         valid path
     */
    private static Path filesystemPath(String given) {
        var path = given.startsWith(FILE_PREFIX) ? given.substring(FILE_PREFIX.length()) : given;
        if (path.isBlank()) {
            throw new BratException("The location '" + given + "' names no path");
        }
        try {
            return Path.of(path).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            throw new BratException("The location '" + given + "' is not a valid path", e);
        }
    }
}
