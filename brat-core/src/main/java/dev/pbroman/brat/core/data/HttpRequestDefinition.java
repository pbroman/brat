package dev.pbroman.brat.core.data;

import dev.pbroman.brat.core.api.RequestDefinition;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.util.ResourceReader;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

import static dev.pbroman.brat.core.util.Constants.BODY_STRING;
import static dev.pbroman.brat.core.util.Constants.FILE_BODY;
import static dev.pbroman.brat.core.util.Constants.RAW_BODY;
import static org.apache.hc.core5.http.ContentType.APPLICATION_FORM_URLENCODED;
import static org.apache.hc.core5.http.HttpHeaders.CONTENT_TYPE;

/**
 * The default, HTTP-based {@link RequestDefinition} implementation.
 */
@Getter
@Setter
public class HttpRequestDefinition extends ConfigData implements RequestDefinition {

    private String url;
    private String method;
    private String timeout;
    private Map<String, String> body;
    private Map<String, String> headers;
    private Auth auth;

    public HttpRequestDefinition(String url,
                                 String method,
                                 String timeout,
                                 Map<String, String> body,
                                 Map<String, String> headers,
                                 Auth auth,
                                 Map<String, InterpolationOutcome> outcomes) {
        super(outcomes);
        this.url = url;
        this.method = method;
        this.timeout = timeout;
        this.body = body;
        this.headers = headers;
        this.auth = auth;
        prepare();
    }

    /**
     * {@code outcomes} defaults to {@code null} (not yet an interpolated copy).
     *
     * @param url the request URL
     * @param method the HTTP method
     * @param timeout the request timeout
     * @param body the request body entries
     * @param headers the request headers
     * @param auth the auth configuration
     */
    public HttpRequestDefinition(String url, String method, String timeout, Map<String, String> body, Map<String, String> headers, Auth auth) {
        this(url, method, timeout, body, headers, auth, null);
    }

    /**
     * Prepares the request definition.
     */
    protected void prepare() {
        if (body != null) {
            if (body.get(RAW_BODY) != null) {
                body.put(BODY_STRING, body.get(RAW_BODY));
            } else if (body.get(FILE_BODY) != null) {
                var bodyFromFile = ResourceReader.readFileToString(body.get(FILE_BODY));
                body.put(BODY_STRING, bodyFromFile);
            }
            if ( headers.get(CONTENT_TYPE).startsWith(APPLICATION_FORM_URLENCODED.getMimeType())) {
                if (!body.isEmpty() && body.get(BODY_STRING) == null) {
                    body.put(BODY_STRING, body.entrySet().stream()
                            .map(e -> e.getKey() + "=" + e.getValue())
                            .reduce((a, b) -> a + "&" + b)
                            .orElse(""));
                }
            }
        }
    }

}
