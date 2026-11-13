package dev.pbroman.brat.core.reporting;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import dev.pbroman.brat.core.api.reporting.ValueMask;
import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.data.result.RequestResult;
import dev.pbroman.brat.core.exception.BratException;
import org.apache.commons.lang3.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static dev.pbroman.brat.core.util.Constants.HEADERS;
import static dev.pbroman.brat.core.util.Constants.JSON;
import static dev.pbroman.brat.core.util.Require.nonNull;

/**
 * Finds the values in one HTTP request and its response that look sensitive, and adds them to a run's
 * {@link ValueMask}.
 * <p>
 * <strong>From the request as sent</strong>, when its definition is an interpolated
 * {@code HttpRequestDefinition}: the value of each {@code Authorization}, {@code Proxy-Authorization}
 * and {@code Cookie} header, the names matched ignoring case; and, for a form body, the value of each
 * entry whose name is one of the sensitive field names below, matched the same way. Each is added as
 * a <em>secret</em> when its outcome ({@code header.<name>} or {@code body.form.<key>})
 * {@code containsSecret}, since it was built from one, and as a <em>guessed</em> value otherwise — an
 * entry with no such outcome included. A form entry's value is added whole.
 * <p>
 * <strong>From the response</strong>, when the request carries one ({@link RequestResult#responseVars()}),
 * every value added as guessed:
 * <ul>
 *   <li>the value of each {@code Set-Cookie} header;</li>
 *   <li>when the body is JSON — the namespace holds a {@code json} entry — every string value of a
 *       field named {@code access_token}, {@code refresh_token}, {@code id_token},
 *       {@code client_secret} or {@code password}, at any depth, in arrays too. A name is matched
 *       ignoring case, {@code _} and {@code -}, so {@code accessToken} and {@code Access-Token} count.
 *       A value that is not a string is not added.</li>
 * </ul>
 * <strong>What is added is the credential, not the whole value</strong>: an {@code Authorization} or
 * {@code Proxy-Authorization} value holding a space contributes what follows its first space, so the
 * scheme word stays readable ({@code Bearer ***}); a {@code Cookie} value contributes the value of each
 * of its {@code name=value} pairs; a {@code Set-Cookie} value the value of its first pair. Any other
 * value is added whole.
 * <p>
 * A request of another protocol contributes nothing, and neither does a body that is not JSON.
 */
public final class SensitiveValues {

    private static final String HEADER_PREFIX = "header.";
    private static final String FORM_PREFIX = "body.form.";
    private static final String SET_COOKIE = "set-cookie";
    private static final Set<String> SCHEME_HEADERS = Set.of("authorization", "proxy-authorization");
    private static final String COOKIE = "cookie";
    /** Field names as {@link #normalised(String)} leaves them. */
    private static final Set<String> SENSITIVE_FIELDS =
            Set.of("accesstoken", "refreshtoken", "idtoken", "clientsecret", "password");

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private SensitiveValues() {
        // static helpers only
    }

    /**
     * Adds the values in {@code result} that look sensitive to {@code mask}, as described above.
     *
     * @param mask the run's mask; must not be {@code null}
     * @param result the finished request; must not be {@code null}
     * @throws BratException if {@code mask} or {@code result} is {@code null}
     */
    public static void addTo(ValueMask mask, RequestResult result) {
        nonNull(mask, "The mask to add to must not be null");
        nonNull(result, "The result to search must not be null");
        if (!(result.requestDefinition() instanceof HttpRequestDefinition sent)) {
            return;
        }
        if (sent.isInterpolated() && sent.getHeaders() != null) {
            addFromRequest(mask, sent);
        }
        if (sent.isInterpolated() && sent.getBody() != null && sent.getBody().getForm() != null) {
            addFromForm(mask, sent);
        }
        result.responseVars().ifPresent(responseVars -> addFromResponse(mask, responseVars));
    }

    private static void addFromRequest(ValueMask mask, HttpRequestDefinition sent) {
        for (var header : sent.getHeaders().entrySet()) {
            var name = header.getKey().toLowerCase(Locale.ROOT);
            if (header.getValue() == null || !(SCHEME_HEADERS.contains(name) || COOKIE.equals(name))) {
                continue;
            }
            var outcome = sent.getOutcomes().get(HEADER_PREFIX + header.getKey());
            var fromSecret = outcome != null && outcome.containsSecret();
            for (var credential : credentials(name, header.getValue())) {
                if (fromSecret) {
                    mask.addSecret(credential);
                } else {
                    mask.addGuessed(credential);
                }
            }
        }
    }

    /**
     * Adds the value of every form entry with a sensitive name, as a secret where its outcome says it
     * was built from one.
     *
     * @param mask the run's mask
     * @param sent the interpolated request, whose body is a form
     */
    private static void addFromForm(ValueMask mask, HttpRequestDefinition sent) {
        for (var entry : sent.getBody().getForm().entrySet()) {
            if (!SENSITIVE_FIELDS.contains(normalised(entry.getKey()))) {
                continue;
            }
            var outcome = sent.getOutcomes().get(FORM_PREFIX + entry.getKey());
            if (outcome != null && outcome.containsSecret()) {
                mask.addSecret(entry.getValue());
            } else {
                mask.addGuessed(entry.getValue());
            }
        }
    }

    private static void addFromResponse(ValueMask mask, Map<String, Object> responseVars) {
        if (responseVars.get(HEADERS) instanceof Map<?, ?> headers) {
            for (var header : headers.entrySet()) {
                if (SET_COOKIE.equalsIgnoreCase(String.valueOf(header.getKey()))
                        && header.getValue() instanceof List<?> values) {
                    for (var value : values) {
                        addGuessedIfPresent(mask, cookieValue(StringUtils.substringBefore(String.valueOf(value), ";")));
                    }
                }
            }
        }
        if (responseVars.get(JSON) != null) {
            try {
                addFields(mask, MAPPER.readTree(responseVars.get(JSON).toString()));
            } catch (JacksonException e) {
                // the namespace only holds json that parsed; nothing to add should it not
            }
        }
    }

    /** Adds every string value of a sensitive field in {@code node}, at any depth. */
    private static void addFields(ValueMask mask, JsonNode node) {
        if (node.isObject()) {
            for (var property : node.properties()) {
                if (property.getValue().isString() && SENSITIVE_FIELDS.contains(normalised(property.getKey()))) {
                    mask.addGuessed(property.getValue().stringValue());
                } else {
                    addFields(mask, property.getValue());
                }
            }
        } else if (node.isArray()) {
            for (var element : node) {
                addFields(mask, element);
            }
        }
    }

    /**
     * The credentials in a scheme or cookie header value: what follows the first space of a scheme
     * header, or the value of each pair of a cookie header.
     */
    private static List<String> credentials(String lowerCaseName, String value) {
        if (SCHEME_HEADERS.contains(lowerCaseName)) {
            var space = value.indexOf(' ');
            return List.of(space > 0 ? value.substring(space + 1).trim() : value);
        }
        return Arrays.stream(value.split(";"))
                .map(SensitiveValues::cookieValue)
                .filter(StringUtils::isNotEmpty)
                .toList();
    }

    /** The value of one {@code name=value} pair, or {@code ""} when it has none. */
    private static String cookieValue(String pair) {
        return StringUtils.substringAfter(pair.trim(), "=").trim();
    }

    private static void addGuessedIfPresent(ValueMask mask, String value) {
        if (!value.isEmpty()) {
            mask.addGuessed(value);
        }
    }

    /** A field name lower case, with {@code _} and {@code -} removed. */
    private static String normalised(String fieldName) {
        return StringUtils.remove(StringUtils.remove(fieldName.toLowerCase(Locale.ROOT), '_'), '-');
    }
}
