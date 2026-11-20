package dev.pbroman.brat.openapi;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The result of an import, in memory: every file it would write, and what its caller needs to tell the
 * author about them.
 * <p>
 * Paths are relative to the directory the scaffold is written to, with {@code /} between their parts
 * on every platform.
 *
 * @param directory the name a directory for the scaffold should have when the caller chooses none: the
 *        file-name form of the spec's title, or {@code imported}
 * @param suiteFile the path of the suite file, the first key of {@code files}
 * @param files every file, path to content, in the order a caller lists them: the suite file, then the
 *        body files, then the environment files in the order of {@link #environments()}. Every content
 *        ends in a newline
 * @param environments the names of the environment directories, in the order of the spec's servers
 * @param requests how many requests the suite holds for the spec's operations, one each; setup requests
 *        the import adds are not counted here
 * @param subSuites how many sub-suites the suite holds
 * @param setupRequests how many setup requests the import added
 * @param notes every place the author has to act, in the order of {@code files} and, within a file, in
 *        the order the places are written; empty if there is none
 */
public record Scaffold(
        String directory,
        String suiteFile,
        Map<String, String> files,
        List<String> environments,
        int requests,
        int subSuites,
        int setupRequests,
        List<Note> notes) {

    /**
     * Constructs a scaffold, keeping copies of its collections that cannot be modified.
     *
     * @param directory the suggested directory name
     * @param suiteFile the path of the suite file
     * @param files every file, path to content; copied in its order
     * @param environments the environment directory names; copied
     * @param requests how many requests stand for operations
     * @param subSuites how many sub-suites there are
     * @param setupRequests how many setup requests were added
     * @param notes the places the author has to act; copied
     */
    public Scaffold {
        files = Collections.unmodifiableMap(new LinkedHashMap<>(files));
        environments = List.copyOf(environments);
        notes = List.copyOf(notes);
    }

    /**
     * One place the author has to act: a value to fill in, a body to write, an assertion to add.
     * <p>
     * A note states the same fact as the comment the import writes beside that place, so a caller can
     * list the places, or jump to them, without reading the files.
     *
     * @param file the path of the file the place is in, a key of {@link Scaffold#files()}
     * @param message what to do there, naming the key or the request it concerns
     */
    public record Note(String file, String message) {}
}
