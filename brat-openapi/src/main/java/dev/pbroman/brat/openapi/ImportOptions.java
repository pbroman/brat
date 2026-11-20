package dev.pbroman.brat.openapi;

import dev.pbroman.brat.core.exception.BratException;

/**
 * The choices an import leaves to its caller.
 *
 * @param bodyFileLines how many lines a JSON request body may have and still be written into the suite;
 *        a longer one goes to a file of its own. {@code 0} sends every JSON body to a file
 */
public record ImportOptions(int bodyFileLines) {

    /** The {@link #bodyFileLines()} of {@link #defaults()}. */
    public static final int DEFAULT_BODY_FILE_LINES = 10;

    /**
     * Constructs the options.
     *
     * @param bodyFileLines how many lines a JSON body may have and stay in the suite; must be {@code 0}
     *        or more
     * @throws BratException if {@code bodyFileLines} is negative, naming the value
     */
    public ImportOptions {
        if (bodyFileLines < 0) {
            throw new BratException(
                    "The number of lines a body may have in the suite must be 0 or more, not " + bodyFileLines);
        }
    }

    /**
     * Returns the options an import uses when its caller chooses nothing.
     *
     * @return options with {@link #DEFAULT_BODY_FILE_LINES}
     */
    public static ImportOptions defaults() {
        return new ImportOptions(DEFAULT_BODY_FILE_LINES);
    }
}
