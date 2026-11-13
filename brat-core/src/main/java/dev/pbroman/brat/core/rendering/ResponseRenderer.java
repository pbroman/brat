package dev.pbroman.brat.core.rendering;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import dev.pbroman.brat.core.api.reporting.ValueMask;
import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.data.result.RequestResult;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.util.HttpHeaderUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static dev.pbroman.brat.core.util.Constants.BODY;
import static dev.pbroman.brat.core.util.Constants.HEADERS;
import static dev.pbroman.brat.core.util.Constants.JSON;
import static dev.pbroman.brat.core.util.Constants.REDIRECT_NOT_FOLLOWED;
import static dev.pbroman.brat.core.util.Constants.STATUS_CODE;
import static dev.pbroman.brat.core.util.Require.nonNull;

/**
 * Renders the response a request received as lines of text for a report.
 * <p>
 * For an HTTP request:
 * <pre>
 * response: 401
 *   Content-Type: application/json
 *   Set-Cookie: SESSION=***; Path=/
 *   body:
 *     {
 *       "error" : "invalid_grant"
 *     }
 * </pre>
 * <ul>
 *   <li><strong>The first line</strong> is {@code response: } and the status code. Nothing else of the
 *       status line is kept by the response's namespace.</li>
 *   <li><strong>The headers</strong> follow, indented by two spaces, one line {@code Name: value} per
 *       value — a repeated header prints once per value — in the namespace's order, which is
 *       alphabetical ignoring case.</li>
 *   <li><strong>The body</strong> follows as a {@code body:} line and the body beneath it, indented by
 *       four spaces. A JSON body — the namespace holds a {@code json} entry — is pretty-printed; any
 *       other text body is shown as it was received. A body whose {@code Content-Type} is not text
 *       prints as one line, {@code body: ‹binary body, <content type>, <N> bytes›}, the size taken from
 *       {@code Content-Length} and left out when that header is absent or not a number. Text is
 *       {@code text/*}, a {@code +json} or {@code +xml} suffix, {@code application/json},
 *       {@code application/xml}, {@code application/javascript} and
 *       {@code application/x-www-form-urlencoded}, compared ignoring case and parameters; a missing
 *       {@code Content-Type} counts as text. An absent or empty body prints no {@code body:} line.</li>
 * </ul>
 * For a request of any other protocol, the first line is {@code response:} alone, followed by one line
 * {@code key: value} per namespace entry, indented by two spaces, sorted by key.
 * <p>
 * <strong>Every header value and the body pass through {@link ReportText#shown}</strong> — the body as
 * a whole, after pretty-printing — so they are masked and cut like every other value a report prints.
 */
public final class ResponseRenderer {

    private static final String INDENT = "  ";
    private static final String BODY_INDENT = INDENT + INDENT;
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String CONTENT_LENGTH = "Content-Length";
    private static final Set<String> TEXT_TYPES = Set.of(
            "application/json", "application/xml", "application/javascript", "application/x-www-form-urlencoded");
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private ResponseRenderer() {
        // static helpers only
    }

    /**
     * Renders the response {@code result} carries.
     *
     * @param result the finished request; must not be {@code null}
     * @param mask the run's mask; must not be {@code null}
     * @param showSecrets whether values the mask only guessed are shown
     * @return the lines, without a line separator and with the indentation described above; empty when
     *         {@code result} carries no response. Never {@code null}
     * @throws BratException if {@code result} or {@code mask} is {@code null}
     */
    public static List<String> lines(RequestResult result, ValueMask mask, boolean showSecrets) {
        nonNull(result, "The result to render the response of must not be null");
        nonNull(mask, "The mask must not be null");
        var responseVars = result.responseVars();
        if (responseVars.isEmpty()) {
            return List.of();
        }
        return result.requestDefinition() instanceof HttpRequestDefinition
                ? http(responseVars.get(), mask, showSecrets)
                : generic(responseVars.get(), mask, showSecrets);
    }

    /**
     * The line saying that a redirect was not followed, for a request whose response records one.
     * <p>
     * {@code REDIRECT <location> not followed — another origin, and the request carries credentials},
     * the location passed through {@link ReportText#shown}. Only an HTTP response records a refused
     * redirect.
     *
     * @param result the finished request; must not be {@code null}
     * @param mask the run's mask; must not be {@code null}
     * @param showSecrets whether values the mask only guessed are shown
     * @return the line, or {@link Optional#empty()} when the request carries no response or its
     *         response refused no redirect
     * @throws BratException if {@code result} or {@code mask} is {@code null}
     */
    public static Optional<String> redirectNotice(RequestResult result, ValueMask mask, boolean showSecrets) {
        nonNull(result, "The result to read a refused redirect from must not be null");
        nonNull(mask, "The mask must not be null");
        return result.responseVars()
                .map(responseVars -> responseVars.get(REDIRECT_NOT_FOLLOWED))
                .map(location -> "REDIRECT " + ReportText.shown(location.toString(), mask, showSecrets)
                        + " not followed — another origin, and the request carries credentials");
    }

    private static List<String> http(Map<String, Object> responseVars, ValueMask mask, boolean showSecrets) {
        var lines = new ArrayList<String>();
        lines.add("response: " + responseVars.get(STATUS_CODE));
        var headers = headersOf(responseVars);
        for (var header : headers.entrySet()) {
            for (var value : header.getValue()) {
                lines.add(INDENT + header.getKey() + ": " + ReportText.shown(value, mask, showSecrets));
            }
        }
        var body = responseVars.get(BODY);
        if (body == null || body.toString().isEmpty()) {
            return lines;
        }
        var contentType = firstValue(headers, CONTENT_TYPE);
        if (contentType != null && !isText(HttpHeaderUtils.mediaType(contentType))) {
            lines.add(INDENT + "body: ‹binary body, " + HttpHeaderUtils.mediaType(contentType) + size(headers) + "›");
            return lines;
        }
        lines.add(INDENT + "body:");
        var text = responseVars.containsKey(JSON) ? pretty(body.toString()) : body.toString();
        for (var line : ReportText.shown(text, mask, showSecrets).lines().toList()) {
            lines.add(BODY_INDENT + line);
        }
        return lines;
    }

    private static List<String> generic(Map<String, Object> responseVars, ValueMask mask, boolean showSecrets) {
        var lines = new ArrayList<String>();
        lines.add("response:");
        for (var entry : new TreeMap<>(responseVars).entrySet()) {
            lines.add(INDENT + entry.getKey() + ": "
                    + ReportText.shown(String.valueOf(entry.getValue()), mask, showSecrets));
        }
        return lines;
    }

    /**
     * The headers of an HTTP response namespace, in the namespace's order; empty when it holds none.
     *
     * @param responseVars the namespace
     * @return the headers
     */
    @SuppressWarnings("unchecked")
    private static Map<String, List<String>> headersOf(Map<String, Object> responseVars) {
        // HttpResponseVars builds this entry as a Map<String, List<String>>.
        return responseVars.get(HEADERS) instanceof Map<?, ?> headers ? (Map<String, List<String>>) headers : Map.of();
    }

    private static String firstValue(Map<String, List<String>> headers, String name) {
        for (var header : headers.entrySet()) {
            if (name.equalsIgnoreCase(header.getKey()) && !header.getValue().isEmpty()) {
                return header.getValue().getFirst();
            }
        }
        return null;
    }

    private static boolean isText(String mediaType) {
        return mediaType.startsWith("text/")
                || mediaType.endsWith("+json")
                || mediaType.endsWith("+xml")
                || TEXT_TYPES.contains(mediaType);
    }

    /** {@code ", N bytes"} from {@code Content-Length}, or nothing when it is absent or not a number. */
    private static String size(Map<String, List<String>> headers) {
        var length = firstValue(headers, CONTENT_LENGTH);
        if (length == null) {
            return "";
        }
        try {
            return ", " + Long.parseLong(length.trim()) + " bytes";
        } catch (NumberFormatException e) {
            return "";
        }
    }

    /** The JSON text pretty-printed; as it is, should it not parse after all. */
    private static String pretty(String json) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(MAPPER.readTree(json));
        } catch (JacksonException e) {
            return json;
        }
    }
}
