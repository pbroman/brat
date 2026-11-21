package dev.pbroman.brat.core.loader;

import dev.pbroman.brat.core.exception.BratException;

/**
 * The limits a YAML document is read within, and the name its messages give it.
 *
 * @param documentName what the document is called in a message, capitalised as a sentence's start:
 *        {@code "The suite document"}; must not be blank
 * @param maxMegabytes the largest document read, in megabytes of text (2<sup>20</sup> code points
 *        each); must be positive
 * @param maxAliases the most aliases to a mapping or a list the document may hold; aliases to a
 *        scalar are not counted. Must be positive
 */
public record YamlLimits(String documentName, int maxMegabytes, int maxAliases) {

    /** The limits a suite file is read within: 3 MB and 50 aliases. */
    public static final YamlLimits SUITE = new YamlLimits("The suite document", 3, 50);

    /**
     * Checks the limits.
     *
     * @throws BratException if {@code documentName} is blank, or either limit is not positive
     */
    public YamlLimits {
        if (documentName == null || documentName.isBlank()) {
            throw new BratException("YAML limits need the document's name");
        }
        if (maxMegabytes <= 0 || maxAliases <= 0) {
            throw new BratException(
                    String.format("YAML limits must be positive, got %d MB and %d aliases", maxMegabytes, maxAliases));
        }
    }
}
