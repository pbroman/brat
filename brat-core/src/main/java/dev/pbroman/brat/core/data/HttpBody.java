package dev.pbroman.brat.core.data;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import dev.pbroman.brat.core.exception.BratException;
import lombok.Getter;

/**
 * The body of an HTTP request: exactly one of a {@code raw} text, a {@code file} to read it from, or a
 * {@code form} that core URL-encodes.
 *
 * <pre>
 * body:
 *   form:
 *     grant_type: client_credentials
 *     client_secret: "${secrets.clientSecret}"
 * </pre>
 *
 * What a request handler sends is {@link #getPayload()}, whichever of the three was declared; it never
 * re-derives the payload or reads a file.
 */
@Getter
public final class HttpBody {

    /** The body text as written, or {@code null} when another kind was declared. */
    private final String raw;

    /** The path of the body file as written, or {@code null} when another kind was declared. */
    private final String file;

    /**
     * The form entries in the order written, unmodifiable, or {@code null} when another kind was
     * declared.
     */
    private final Map<String, String> form;

    /** The content of {@link #file} once it has been read, or {@code null} until then. */
    private final String fileContent;

    /**
     * Constructs a body as a suite declares it.
     *
     * @param raw the body text, or {@code null}
     * @param file the path of a file holding the body, or {@code null}
     * @param form the form entries, or {@code null}. Copied, keeping their order
     * @throws BratException if not exactly one of {@code raw}, {@code file} and {@code form} is given —
     *         naming those that were, or, when none was, saying so and pointing at the key's spelling,
     *         since a misspelt key reaches this constructor before the loader reports it; or if
     *         {@code form} holds a {@code null} key or a {@code null} value, naming the key. No message
     *         quotes a value
     */
    @JsonCreator
    public HttpBody(String raw, String file, Map<String, String> form) {
        this(raw, file, form, null);
    }

    /**
     * Constructs a body carrying the content its file was read to — the form a body takes on an
     * interpolated request.
     *
     * @param raw the body text, or {@code null}
     * @param file the path of a file holding the body, or {@code null}
     * @param form the form entries, or {@code null}. Copied, keeping their order
     * @param fileContent the content {@code file} was read to, or {@code null}
     * @throws BratException under the conditions of {@link #HttpBody(String, String, Map)}, or if
     *         {@code fileContent} is given for a body that declares no {@code file}
     */
    public HttpBody(String raw, String file, Map<String, String> form, String fileContent) {
        var declared = (raw != null ? "raw " : "") + (file != null ? "file " : "") + (form != null ? "form " : "");
        if (declared.isEmpty()) {
            throw new BratException(
                    "The request declares a body with none of raw, file or form in it. Check the key's spelling");
        }
        if (declared.indexOf(' ') != declared.length() - 1) {
            throw new BratException(
                    "The request declares a body as " + declared.trim().replace(" ", " and ")
                            + ", which states two payloads for one request. Declare one of raw, file or form");
        }
        if (fileContent != null && file == null) {
            throw new BratException("Only a file body carries file content");
        }
        if (form != null) {
            for (var entry : form.entrySet()) {
                if (entry.getKey() == null) {
                    throw new BratException("A form entry has no name");
                }
                if (entry.getValue() == null) {
                    throw new BratException(
                            "The form entry '" + entry.getKey() + "' has no value. Write \"\" to send an empty one");
                }
            }
        }
        this.raw = raw;
        this.file = file;
        this.form = form == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(form));
        this.fileContent = fileContent;
    }

    /**
     * A body sending {@code raw} as it is.
     *
     * @param raw the body text; must not be {@code null}
     * @return the body
     * @throws BratException if {@code raw} is {@code null}
     */
    public static HttpBody raw(String raw) {
        return new HttpBody(raw, null, null);
    }

    /**
     * A body read from the file at {@code file}.
     *
     * @param file the path, resolved as a suite's {@code file:} body is; must not be {@code null}
     * @return the body
     * @throws BratException if {@code file} is {@code null}
     */
    public static HttpBody file(String file) {
        return new HttpBody(null, file, null);
    }

    /**
     * A body sending {@code form} URL-encoded.
     *
     * @param form the form entries, in the order they are sent; must not be {@code null}
     * @return the body
     * @throws BratException if {@code form} is {@code null}, or holds a {@code null} key or value
     */
    public static HttpBody form(Map<String, String> form) {
        return new HttpBody(null, null, form);
    }

    /**
     * The text to send.
     * <ul>
     *   <li>{@code raw}: the text as it is;</li>
     *   <li>{@code file}: {@link #getFileContent()}, which is {@code null} until the file has been read
     *       — the request is interpolated before it is sent, and that is when the file is read;</li>
     *   <li>{@code form}: every entry as {@code key=value}, joined by {@code &} in the order written, each
     *       key and value encoded with {@link java.net.URLEncoder} in UTF-8 — so a space becomes
     *       {@code +}, and {@code &}, {@code =} and non-ASCII characters are percent-encoded. An entry
     *       with an empty value is {@code key=}; a form with no entries is the empty string.</li>
     * </ul>
     * The form is encoded from the values this body holds, so on an interpolated request it is the
     * resolved values that are encoded, and a value holding {@code &} arrives intact.
     *
     * @return the payload, or {@code null} for a file body not yet read
     */
    public String getPayload() {
        if (raw != null) {
            return raw;
        }
        if (file != null) {
            return fileContent;
        }
        return form.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
