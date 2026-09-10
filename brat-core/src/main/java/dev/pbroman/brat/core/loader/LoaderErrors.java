package dev.pbroman.brat.core.loader;

import dev.pbroman.brat.core.exception.BratException;

/**
 * Builds the loader's errors, so that every one of them reads the same and obeys the same rule.
 * <p>
 * <strong>An error names the key and where it sat, and never echoes the value.</strong> A suite file
 * may hold a literal credential and a body file more so, and an error message travels into logs and
 * CI output: the key is what an author needs, the value is what leaks. Shared by binding and by
 * {@link SuiteChecks} because both must obey it — a second copy is a second chance to forget.
 */
final class LoaderErrors {

    private LoaderErrors() {
        // utility class
    }

    /**
     * Builds an error naming the document, the problem and where it sat.
     *
     * @param origin what to call the document, or {@code null} for content with no name
     * @param document the converted document, for its position index
     * @param pointer the JSON Pointer of the offending value
     * @param message what is wrong, naming keys but never values
     * @return the exception to throw
     */
    static BratException at(String origin, YamlDocument document, String pointer, String message) {
        var where = document.positions()
                .at(pointer)
                .map(position -> " at " + position)
                .orElse("");
        var named = origin == null ? "" : origin + ": ";
        return new BratException(named + message + where);
    }
}
