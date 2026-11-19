package dev.pbroman.brat.core.api.reporting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import dev.pbroman.brat.core.exception.BratException;

import static dev.pbroman.brat.core.util.Constants.MASK;
import static dev.pbroman.brat.core.util.Require.nonNull;

/**
 * The values one run must not print, and the masking of them in any text a report prints.
 * <p>
 * It holds two kinds of value:
 * <ul>
 *   <li><strong>secrets</strong> — what a secrets provider returned during the run, and what was built
 *       from it. Always masked;</li>
 *   <li><strong>guessed</strong> values — values that only look sensitive, such as an
 *       {@code access_token} in a response or a credential typed into a suite in clear. Masked unless
 *       the report was asked to show them.</li>
 * </ul>
 * The run fills it as it goes and hands it to every listener on {@code RunStarted}, so a value is
 * masked in everything rendered after it was added — including the rest of the run, wherever the
 * value turns up again. A value transformed on the way (encoded, cut, upper-cased) is not
 * recognised.
 * <p>
 * Safe to add to and mask with from several threads at once.
 */
public final class ValueMask {

    /** The shortest value masked: a shorter one would mask its every occurrence in ordinary text. */
    public static final int MIN_LENGTH = 4;

    private final Set<String> secrets = ConcurrentHashMap.newKeySet();
    private final Set<String> guessed = ConcurrentHashMap.newKeySet();

    /** Constructs a mask holding no values, which masks nothing. */
    public ValueMask() {
        // empty until the run adds to it
    }

    /**
     * Adds a value that is always masked.
     * <p>
     * A value shorter than {@link #MIN_LENGTH} is accepted and never masked. Adding a value twice, or
     * adding a guessed value as a secret, is allowed; a value that is both is masked as a secret.
     *
     * @param value the value; must not be {@code null}
     * @throws BratException if {@code value} is {@code null}
     */
    public void addSecret(String value) {
        nonNull(value, "A secret to mask must not be null");
        secrets.add(value);
    }

    /**
     * Adds a value that is masked unless a report is asked to show it.
     * <p>
     * A value shorter than {@link #MIN_LENGTH} is accepted and never masked. Adding a value twice is
     * allowed.
     *
     * @param value the value; must not be {@code null}
     * @throws BratException if {@code value} is {@code null}
     */
    public void addGuessed(String value) {
        nonNull(value, "A value to mask must not be null");
        guessed.add(value);
    }

    /**
     * Returns {@code text} with every occurrence of every masked value replaced by {@code ***}.
     * <p>
     * The masked values are every secret, and — unless {@code showSecrets} — every guessed value, each
     * of at least {@link #MIN_LENGTH} characters. Longer values are replaced first, so a value that
     * contains another is masked whole rather than around it. Matching is exact: case and every
     * character count. Text holding none of them is returned as it is.
     *
     * @param text the text to mask; must not be {@code null}
     * @param showSecrets whether guessed values are left in the text
     * @return the masked text; never {@code null}
     * @throws BratException if {@code text} is {@code null}
     */
    public String mask(String text, boolean showSecrets) {
        nonNull(text, "The text to mask must not be null");
        var values = new ArrayList<>(secrets);
        if (!showSecrets) {
            values.addAll(guessed);
        }
        values.sort(Comparator.comparingInt(String::length).reversed());
        var masked = text;
        for (var value : values) {
            if (value.length() >= MIN_LENGTH) {
                masked = masked.replace(value, MASK);
            }
        }
        return masked;
    }
}
