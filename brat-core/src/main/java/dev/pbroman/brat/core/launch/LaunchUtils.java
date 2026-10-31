package dev.pbroman.brat.core.launch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import dev.pbroman.brat.core.exception.BratException;

/**
 * Static helpers the launch layer shares for reading the directories it is given.
 */
final class LaunchUtils {

    private LaunchUtils() {
        // utility class
    }

    /**
     * Checks that a directory exists and that it is a directory.
     *
     * @param path the directory
     * @param given the directory as given, for messages
     * @param kind what the directory is, for messages — {@code environment}, {@code suite}
     * @throws BratException if {@code path} does not exist, or is not a directory — naming the kind,
     *         {@code given} and {@code path}
     */
    static void checkDirectory(Path path, String given, String kind) {
        if (!Files.exists(path)) {
            throw new BratException(String.format("The %s directory '%s' does not exist (%s)", kind, given, path));
        }
        if (!Files.isDirectory(path)) {
            throw new BratException(String.format("The %s directory '%s' is not a directory (%s)", kind, given, path));
        }
    }

    /**
     * Lists the regular files directly in a directory, sorted by file name.
     * <p>
     * Subdirectories are not entered and are not listed, whatever their name. Names are compared by
     * {@link String#compareTo(String)}, never by locale, so the order is the same on every machine:
     * digits before uppercase before lowercase, and {@code secrets.yaml} before {@code secrets001.yaml}.
     *
     * @param path the directory
     * @param given the directory as given, for messages
     * @param kind what the directory is, for messages — {@code environment}, {@code suite}
     * @return the regular files directly in {@code path}, sorted by file name; empty if there are none
     * @throws BratException if {@code path} does not exist or is not a directory, as
     *         {@link #checkDirectory} says; or if it cannot be listed, naming the kind and {@code given}
     */
    static List<Path> regularFilesIn(Path path, String given, String kind) {
        checkDirectory(path, given, kind);
        try (var listing = Files.list(path)) {
            return listing.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(file -> String.valueOf(file.getFileName())))
                    .toList();
        } catch (IOException e) {
            throw new BratException(String.format("The %s directory '%s' cannot be listed", kind, given), e);
        }
    }

    /**
     * The file's own name, without its directory.
     *
     * @param file a file listed from a directory, which always has a name
     * @return its name
     */
    static String fileName(Path file) {
        return String.valueOf(file.getFileName());
    }
}
