package dev.pbroman.brat.core.rendering;

import dev.pbroman.brat.core.api.reporting.ValueMask;
import dev.pbroman.brat.core.exception.BratException;

import static dev.pbroman.brat.core.util.Require.nonNull;

/**
 * Prepares one value for a report: masks it, then cuts it to a readable length.
 * <p>
 * Every value a report prints from a run's data — an outcome, a header, a body, a message — goes
 * through {@link #shown}, so masking and the length limit apply alike wherever it appears.
 */
public final class ReportText {

    /** The most characters of one value a report prints. */
    public static final int MAX_LENGTH = 4096;

    private ReportText() {
        // static helpers only
    }

    /**
     * Returns {@code text} masked by {@code mask}, then cut to {@link #MAX_LENGTH} characters.
     * <p>
     * Masking comes first, so a cut never exposes part of a masked value. Text of at most
     * {@link #MAX_LENGTH} characters after masking is returned whole. Longer text keeps its first
     * {@link #MAX_LENGTH} characters — one fewer where the last would be the first half of a surrogate
     * pair, so no character is split — followed by a line separator and {@code … N more characters},
     * where {@code N} is how many were cut.
     *
     * @param text the value to show; must not be {@code null}
     * @param mask the run's mask; must not be {@code null}
     * @param showSecrets whether values the mask only guessed are left in, as for
     *        {@link ValueMask#mask(String, boolean)}
     * @return the text to print; never {@code null}
     * @throws BratException if {@code text} or {@code mask} is {@code null}
     */
    public static String shown(String text, ValueMask mask, boolean showSecrets) {
        nonNull(text, "The text to show must not be null");
        nonNull(mask, "The mask must not be null");
        var masked = mask.mask(text, showSecrets);
        if (masked.length() <= MAX_LENGTH) {
            return masked;
        }
        var kept = Character.isHighSurrogate(masked.charAt(MAX_LENGTH - 1)) ? MAX_LENGTH - 1 : MAX_LENGTH;
        return masked.substring(0, kept) + System.lineSeparator() + "… " + (masked.length() - kept)
                + " more characters";
    }
}
