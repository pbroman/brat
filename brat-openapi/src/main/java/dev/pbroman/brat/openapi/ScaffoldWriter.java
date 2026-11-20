package dev.pbroman.brat.openapi;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.util.Require;

/**
 * Writes a {@link Scaffold} to disk, never overwriting anything.
 * <p>
 * It holds no state, so it may be called from several threads at once — for different directories.
 */
public final class ScaffoldWriter {

    /** How many of a non-empty directory's entries a refusal names. */
    private static final int SHOWN = 5;

    private ScaffoldWriter() {
        // static entry point only
    }

    /**
     * Writes every file of {@code scaffold} under {@code directory}, as UTF-8, creating the directory,
     * its parents and every subdirectory a file's path needs.
     * <p>
     * <strong>It refuses rather than overwrites.</strong> {@code directory} must not exist, or be an
     * empty directory, and every check is made before anything is written. Each file is created new, so
     * one that appears while the scaffold is being written is not overwritten either: the write fails
     * on it, as for any other failure to write.
     *
     * @param scaffold the files to write; must not be {@code null}
     * @param directory where to write them; must not be {@code null}
     * @return the path of every file written, {@code directory} resolved against each of the scaffold's
     *         paths, in the scaffold's order
     * @throws BratException
     *         <ul>
     *           <li>if {@code scaffold} or {@code directory} is {@code null};</li>
     *           <li>if {@code directory} exists and is not a directory, naming it;</li>
     *           <li>if {@code directory} holds anything, naming it and what it holds: up to five names,
     *               sorted, followed by how many more there are;</li>
     *           <li>if a path of the scaffold is empty, is not a path the file system accepts, is
     *               absolute, or does not name a file below {@code directory} — {@code .} names the
     *               directory itself, {@code ..} leads outside it — naming the path as the scaffold has
     *               it;</li>
     *           <li>if a directory or a file cannot be created or written, naming the file by its path
     *               in the scaffold, and the reason — where something already stands in the way, naming
     *               that. The files written before it stay.</li>
     *         </ul>
     *         Nothing is written in any case but the last.
     */
    public static List<Path> write(Scaffold scaffold, Path directory) {
        Require.nonNull(scaffold, "The scaffold to write must not be null");
        Require.nonNull(directory, "The directory to write the scaffold to must not be null");
        checkEmpty(directory);
        var targets = new LinkedHashMap<String, Path>();
        var root = directory.toAbsolutePath().normalize();
        for (var path : scaffold.files().keySet()) {
            targets.put(path, target(root, directory, path));
        }
        var written = new ArrayList<Path>();
        for (var entry : targets.entrySet()) {
            var target = entry.getValue();
            try {
                var parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(
                        target,
                        scaffold.files().get(entry.getKey()),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE);
            } catch (IOException e) {
                throw new BratException(
                        "Could not write '" + entry.getKey() + "' in " + directory + ": " + reason(e), e);
            }
            written.add(target);
        }
        return List.copyOf(written);
    }

    private static void checkEmpty(Path directory) {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (!Files.isDirectory(directory)) {
            throw new BratException(directory + " exists and is not a directory; name a new or an empty one");
        }
        List<String> names;
        try (var entries = Files.list(directory)) {
            names = entries.map(entry -> String.valueOf(entry.getFileName()))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new BratException("Could not read the directory " + directory + ": " + reason(e), e);
        }
        if (names.isEmpty()) {
            return;
        }
        var shown = String.join(", ", names.subList(0, Math.min(SHOWN, names.size())));
        var more = names.size() > SHOWN ? " and " + (names.size() - SHOWN) + " more" : "";
        throw new BratException(directory + " is not empty (it holds " + shown + more
                + "); nothing was written. Name a new or an empty directory");
    }

    /** Where the scaffold's {@code path} goes, refusing one that would not stay below the directory. */
    private static Path target(Path root, Path directory, String path) {
        Path relative;
        try {
            relative = Path.of(path);
        } catch (InvalidPathException e) {
            throw new BratException("The scaffold's path '" + path + "' is not a valid path", e);
        }
        var target = root.resolve(relative).normalize();
        if (path.isEmpty() || relative.isAbsolute() || !target.startsWith(root) || target.equals(root)) {
            throw new BratException("The scaffold's path '" + path + "' does not name a file inside " + directory
                    + "; nothing was written");
        }
        return directory.resolve(root.relativize(target));
    }

    private static String reason(IOException e) {
        return e instanceof FileAlreadyExistsException exists
                ? exists.getFile() + " is already there"
                : e.getClass().getSimpleName() + " " + e.getMessage();
    }
}
