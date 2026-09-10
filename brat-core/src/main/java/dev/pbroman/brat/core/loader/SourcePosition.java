package dev.pbroman.brat.core.loader;

/**
 * Where something sits in the document an author wrote.
 *
 * @param line the 1-based line
 * @param column the 1-based column
 */
record SourcePosition(int line, int column) {

    /**
     * Renders the position as {@code line L, column C}, which is how it appears in a loader error.
     *
     * @return the rendered position
     */
    @Override
    public String toString() {
        return "line " + line + ", column " + column;
    }
}
