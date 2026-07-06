package dev.pbroman.brat.core.data.result;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The result of executing an HTTP request.
 * <p>
 * {@code headers} lookups are case-insensitive, per the HTTP spec, regardless of the case the
 * server actually sent (some send {@code content-type}, others {@code Content-Type}). Multiple
 * values for the same header name are preserved in full. A {@code null} {@code headers}
 * argument is treated as empty rather than rejected. The returned {@code headers} map is always
 * unmodifiable, independent of whether the map passed in was.
 */
public record HttpResponse(int statusCode, Map<String, List<String>> headers, String body) {

    public HttpResponse {
        var caseInsensitiveHeaders = new TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER);
        if (headers != null) {
            headers.forEach((name, values) -> caseInsensitiveHeaders.put(name, List.copyOf(values)));
        }
        headers = Collections.unmodifiableMap(caseInsensitiveHeaders);
    }
}
