package dev.pbroman.brat.openapi;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Static helpers for writing text taken from a spec into a YAML document without changing what it
 * means.
 */
final class YamlText {

    private static final Pattern PLAIN = Pattern.compile("[A-Za-z][A-Za-z0-9 ._~/:@?=&%+,()-]*");

    private static final Set<String> RESERVED_WORDS =
            Set.of("true", "false", "yes", "no", "on", "off", "y", "n", "null");

    private YamlText() {
        // static helpers only
    }

    /**
     * Writes {@code value} as a YAML scalar that reads back as exactly that text, usable both as a value
     * and as a mapping key in block style.
     * <p>
     * It is written plain — as it is — when YAML would read it as that same text anyway: it starts with
     * an ASCII letter; holds only ASCII letters, digits, spaces and {@code ._~/:@?=&%+,()-}; does not end
     * with a space or a {@code :}; holds no {@code ": "} and no {@code " #"}; and is not, in any case,
     * {@code true}, {@code false}, {@code yes}, {@code no}, {@code on}, {@code off}, {@code y},
     * {@code n} or {@code null}. So {@code OPEN} and {@code http://localhost:8080} stay as they are.
     * <p>
     * Everything else is double-quoted, with {@code \} and {@code "} escaped and every character below
     * U+0020, U+007F, U+0085, U+2028 and U+2029 written as a YAML escape such as {@code \n} or
     * {@code \t} — so {@code 4711},
     * {@code yes}, {@code a: b}, {@code ${env.x}} and the empty text are all quoted.
     *
     * @param value the text; must not be {@code null}
     * @return the scalar, on one line
     */
    static String scalar(String value) {
        if (isPlain(value)) {
            return value;
        }
        var quoted = new StringBuilder(value.length() + 2).append('"');
        for (var i = 0; i < value.length(); i++) {
            var c = value.charAt(i);
            switch (c) {
                case '\\' -> quoted.append("\\\\");
                case '"' -> quoted.append("\\\"");
                case '\n' -> quoted.append("\\n");
                case '\t' -> quoted.append("\\t");
                case '\r' -> quoted.append("\\r");
                case '\u0085' -> quoted.append("\\N");
                case '\u2028', '\u2029' -> quoted.append(String.format("\\u%04X", (int) c));
                default -> {
                    if (c < ' ' || c == '\u007f') {
                        quoted.append(String.format("\\x%02X", (int) c));
                    } else {
                        quoted.append(c);
                    }
                }
            }
        }
        return quoted.append('"').toString();
    }

    private static boolean isPlain(String value) {
        return PLAIN.matcher(value).matches()
                && !value.endsWith(" ")
                && !value.endsWith(":")
                && !value.contains(": ")
                && !value.contains(" #")
                && !RESERVED_WORDS.contains(value.toLowerCase(Locale.ROOT));
    }

    /**
     * Makes {@code text} safe to write after a {@code #} on one line: every character below U+0020, and
     * U+0085, U+2028 and U+2029, becomes a space.
     *
     * @param text the text; must not be {@code null}
     * @return the text, on one line
     */
    static String commentText(String text) {
        var result = new StringBuilder(text.length());
        for (var i = 0; i < text.length(); i++) {
            var c = text.charAt(i);
            result.append(c < ' ' || c == '\u0085' || c == '\u2028' || c == '\u2029' ? ' ' : c);
        }
        return result.toString();
    }
}
