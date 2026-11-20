package dev.pbroman.brat.junit;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.util.Require;

/**
 * Static helpers finding the file or directory a test names by a relative path, wherever in the
 * repository the test runs from.
 */
final class SuiteLocator {

    /** What marks a repository's root: a directory, or a file in a worktree or a submodule. */
    private static final String GIT = ".git";

    private SuiteLocator() {
        // static helpers only
    }

    /**
     * Finds {@code path} in {@code start} or a directory above it.
     * <p>
     * <strong>An absolute path</strong> is returned as given, never searched, whether or not it exists.
     * <p>
     * <strong>A relative path</strong> is looked for in {@code start}, then in each directory above it,
     * and the first directory it exists in wins: the result is that directory with {@code path} resolved
     * against it. The search stops after the nearest directory holding {@code .git} — a directory, or a
     * file as in a worktree or a submodule — so nothing outside the repository matches; without one, it
     * goes on up to the filesystem root. It climbs {@code start} as given, made absolute but with no
     * symbolic link resolved, and only ever checks whether {@code path} exists in each directory: a
     * directory it may not read counts as not holding it, and the search goes on.
     *
     * @param path the file or directory to find; must not be {@code null} or blank
     * @param start the directory the search starts in, typically the working directory; must not be
     *        {@code null}
     * @return the path found, or {@code path} itself when it is absolute
     * @throws BratException if {@code path} is {@code null} or blank, or is not a path; if {@code start}
     *         is {@code null}; or if a relative {@code path} exists nowhere the search looked, naming
     *         {@code path}, {@code start}, the directory the search stopped at and whether that is the
     *         repository root or the filesystem root:
     *         {@code No 'api-tests/orders-api' in /work/svc/service or any directory above it, up to
     *         /work/svc (the repository root)}
     */
    static Path locate(String path, Path start) {
        Require.nonBlank(path, "The path to find must not be blank");
        Require.nonNull(start, "The directory to search from must not be null");
        Path relative;
        try {
            relative = Path.of(path);
        } catch (InvalidPathException e) {
            throw new BratException("'" + path + "' is not a path: " + e.getReason(), e);
        }
        if (relative.isAbsolute()) {
            return relative;
        }
        var from = start.toAbsolutePath();
        var dir = from;
        while (true) {
            var candidate = dir.resolve(relative);
            if (Files.exists(candidate)) {
                return candidate;
            }
            if (Files.exists(dir.resolve(GIT))) {
                throw notFound(path, from, dir, "the repository root");
            }
            var parent = dir.getParent();
            if (parent == null) {
                throw notFound(path, from, dir, "the filesystem root");
            }
            dir = parent;
        }
    }

    private static BratException notFound(String path, Path from, Path stoppedAt, String why) {
        return new BratException(
                "No '" + path + "' in " + from + " or any directory above it, up to " + stoppedAt + " (" + why + ")");
    }
}
