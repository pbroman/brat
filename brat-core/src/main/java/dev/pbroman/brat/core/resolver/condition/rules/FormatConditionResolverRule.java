package dev.pbroman.brat.core.resolver.condition.rules;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.DateTimeException;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import dev.pbroman.brat.core.api.resolver.ConditionPredicate;
import org.apache.commons.lang3.StringUtils;

import static dev.pbroman.brat.core.util.Constants.FORMAT_CONDITION;
import static dev.pbroman.brat.core.util.Constants.FORMAT_EMAIL;
import static dev.pbroman.brat.core.util.Constants.FORMAT_ISO_DATE_TIME;
import static dev.pbroman.brat.core.util.Constants.FORMAT_URL;
import static dev.pbroman.brat.core.util.Constants.FORMAT_UUID;

/**
 * Core resolver for the "is this value well-formed" conditions: {@code isUuid}, {@code isEmail},
 * {@code isIsoDateTime} and {@code isUrl}.
 * <p>
 * All four are unary — they judge {@code a} alone and take no {@code b}. Each answers a shape
 * question rather than a value question, which is why they are worth having over {@code matches}
 * with a handwritten regex at every call site.
 * <p>
 * This rule declares no priority: priorities order the decline chain for a func name several
 * categories answer to, and these four names are its alone.
 */
public final class FormatConditionResolverRule extends AbstractConditionResolverRule {

    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    /**
     * Deliberately a shape check rather than RFC 5322: something before an {@code @}, something
     * after it, a dot in the domain, and no whitespace anywhere. Full RFC compliance accepts
     * addresses no API under test will ever return, and rejecting a valid oddity is the worse
     * failure for an assertion library.
     */
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    /**
     * Constructs the format condition rule with its predicates.
     */
    public FormatConditionResolverRule() {
        super(predicates());
    }

    private static Map<String, ConditionPredicate> predicates() {
        var predicates = new HashMap<String, ConditionPredicate>();
        predicates.put(
                FORMAT_UUID, (a, b, args) -> UUID_PATTERN.matcher(parse(a)).matches());
        predicates.put(
                FORMAT_EMAIL, (a, b, args) -> EMAIL_PATTERN.matcher(parse(a)).matches());
        predicates.put(FORMAT_ISO_DATE_TIME, (a, b, args) -> isIsoDateTime(parse(a)));
        predicates.put(FORMAT_URL, (a, b, args) -> isUrl(parse(a)));
        return predicates;
    }

    private static String parse(Object value) {
        return String.valueOf(value);
    }

    /**
     * A date <em>and</em> a time, per {@link DateTimeFormatter#ISO_DATE_TIME} — with or without an
     * offset or zone. A date on its own is not one, which is what the func name says.
     * <p>
     * Catches {@link DateTimeException} rather than only {@link java.time.format.DateTimeParseException}: a shape
     * check must answer {@code false}, never abort the assertion, and the narrower catch would let
     * any other date-time failure escape as a thrown condition.
     */
    private static boolean isIsoDateTime(String value) {
        try {
            DateTimeFormatter.ISO_DATE_TIME.parse(value);
            return true;
        } catch (DateTimeException e) {
            return false;
        }
    }

    /**
     * Absolute, with a scheme and an authority — so {@code /orders/1} (relative) and
     * {@code mailto:a@b.com} (no authority) are not URLs for this purpose, which is what a suite
     * asserting on a link or a redirect target means.
     * <p>
     * The check is on the authority rather than {@link URI#getHost()} deliberately: the JDK returns
     * a {@code null} host for an authority containing an underscore, so {@code getHost} would call
     * {@code http://order_service:8080} malformed. Underscored hostnames are invalid to the letter
     * of the spec and everyday in container and internal-service names, and answering "not a URL"
     * for one is indistinguishable from a typo to whoever wrote the assertion.
     */
    private static boolean isUrl(String value) {
        try {
            var uri = new URI(value);
            return uri.isAbsolute() && StringUtils.isNotBlank(uri.getAuthority());
        } catch (URISyntaxException e) {
            return false;
        }
    }

    @Override
    public String category() {
        return FORMAT_CONDITION;
    }

    @Override
    protected List<String> ignoreBNullCheck() {
        return List.of(FORMAT_UUID, FORMAT_EMAIL, FORMAT_ISO_DATE_TIME, FORMAT_URL);
    }
}
