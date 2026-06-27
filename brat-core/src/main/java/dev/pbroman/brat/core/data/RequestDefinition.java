package dev.pbroman.brat.core.data;

import static dev.pbroman.brat.core.util.Constants.BODY_STRING;
import static dev.pbroman.brat.core.util.Constants.FILE_BODY;
import static dev.pbroman.brat.core.util.Constants.RAW_BODY;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.util.ResourceReader;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RequestDefinition extends ConfigData {

    private String url;
    private String method;
    private String timeout;
    private Map<String, String> body;
    private Map<String, String> headers;
    private Auth auth;
    private final RequestDefinition nonInterpolated;

    public RequestDefinition(String url,
                             String method,
                             String timeout,
                             Map<String, String> body,
                             Map<String, String> headers,
                             Auth auth,
                             RequestDefinition nonInterpolated) {
        this.url = url;
        this.method = method;
        this.timeout = timeout;
        this.body = body;
        this.headers = headers;
        this.auth = auth;
        this.nonInterpolated = nonInterpolated;
        prepare();
    }

    public RequestDefinition(String url, String method, String timeout, Map<String, String> body, Map<String, String> headers, Auth auth) {
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
            if ( headers.get(HttpHeaders.CONTENT_TYPE).startsWith(MediaType.APPLICATION_FORM_URLENCODED_VALUE)) {
                if (!body.isEmpty() && body.get(BODY_STRING) == null) {
                    body.put(BODY_STRING, body.entrySet().stream()
                            .map(e -> e.getKey() + "=" + e.getValue())
                            .reduce((a, b) -> a + "&" + b)
                            .orElse(""));
                }
            }
        }
    }

    @Override
    public RequestDefinition interpolated(Interpolation interpolation, RuntimeData runtimeData) {
        if (nonInterpolated != null) {
            throw new IllegalStateException(String.format("This RequestDefinition (%s) is already an interpolated copy", this));
        }
        return new RequestDefinition(
                interpolation.interpolate(url, runtimeData),
                interpolation.interpolate(method, runtimeData),
                interpolation.interpolate(timeout, runtimeData),
                interpolateMap(interpolation, runtimeData, body),
                interpolateMap(interpolation, runtimeData, headers),
                auth.interpolated(interpolation, runtimeData),
                this);
    }

}
