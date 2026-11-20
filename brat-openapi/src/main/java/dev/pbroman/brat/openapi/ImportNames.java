package dev.pbroman.brat.openapi;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import dev.pbroman.brat.openapi.OpenApiDocument.Operation;
import org.apache.commons.lang3.StringUtils;

/**
 * Static helpers for the names an import writes: file names, suite and request names, and making names
 * unique among their siblings.
 */
final class ImportNames {

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");

    private static final Pattern NOT_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private ImportNames() {
        // static helpers only
    }

    /**
     * Makes {@code text} into a name safe for a file or a directory on every platform.
     * <p>
     * Accents are removed from letters ({@code é} becomes {@code e}), letters are made lower case, and
     * every run of characters other than an ASCII letter or digit becomes one {@code -}. A {@code -} at
     * the start or the end is removed. So {@code Orders API} is {@code orders-api}, and
     * {@code GET orders {orderId}} is {@code get-orders-orderid}.
     *
     * @param text the text; may be {@code null}
     * @return the name; empty if {@code text} is {@code null} or nothing of it is left
     */
    static String fileName(String text) {
        if (text == null) {
            return "";
        }
        var unaccented =
                MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("");
        var hyphenated =
                NOT_ALPHANUMERIC.matcher(unaccented.toLowerCase(Locale.ROOT)).replaceAll("-");
        return StringUtils.strip(hyphenated, "-");
    }

    /**
     * Makes {@code text} into a name a suite or a request may have: each {@code /} becomes a space, each
     * run of whitespace one space, and whitespace at either end is removed.
     *
     * @param text the text; may be {@code null}
     * @return the name; empty if {@code text} is {@code null} or holds nothing else
     */
    static String nodeName(String text) {
        if (text == null) {
            return "";
        }
        return WHITESPACE.matcher(text.replace('/', ' ')).replaceAll(" ").strip();
    }

    /**
     * Names the request for {@code operation}: its {@code summary}, else its {@code operationId}, else
     * its method and its path with each {@code /} a space ({@code GET orders {orderId}}), always made a
     * {@linkplain #nodeName(String) node name}. A summary or an operation id that is blank, or that no
     * node name is left of, counts as absent.
     *
     * @param operation the operation; must not be {@code null}
     * @return the request's name, never blank
     */
    static String requestName(Operation operation) {
        var summary = nodeName(operation.summary());
        if (!summary.isEmpty()) {
            return summary;
        }
        var operationId = nodeName(operation.operationId());
        if (!operationId.isEmpty()) {
            return operationId;
        }
        return nodeName(operation.method() + " " + operation.path());
    }

    /**
     * Makes each of {@code names} unique, in order.
     * <p>
     * A name is kept if no earlier result and nothing in {@code taken} has it. Otherwise it gets the
     * lowest number from {@code 2} on, behind {@code separator}, that gives a name no earlier result, no
     * name in {@code taken} and no later entry of {@code names} has — so {@code a, a, a 2} becomes
     * {@code a, a 3, a 2}, the third keeping the name it came with.
     *
     * @param names the names, in the order they claim theirs; must not be {@code null} or hold
     *        {@code null}. Not modified
     * @param separator what goes between a name and its number, such as {@code " "} or {@code "-"}; must
     *        not be {@code null}
     * @param taken names already in use, which no result may have; must not be {@code null}. Not
     *        modified
     * @return the unique names, one per entry of {@code names}, in its order
     */
    static List<String> unique(List<String> names, String separator, Collection<String> taken) {
        var used = new HashSet<>(taken);
        var result = new ArrayList<String>(names.size());
        for (var i = 0; i < names.size(); i++) {
            var name = names.get(i);
            var unique = name;
            if (used.contains(name)) {
                var later = names.subList(i + 1, names.size());
                var number = 2;
                while (used.contains(name + separator + number) || later.contains(name + separator + number)) {
                    number++;
                }
                unique = name + separator + number;
            }
            used.add(unique);
            result.add(unique);
        }
        return result;
    }
}
