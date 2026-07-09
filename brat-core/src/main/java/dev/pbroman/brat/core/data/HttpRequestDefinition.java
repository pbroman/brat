package dev.pbroman.brat.core.data;

import dev.pbroman.brat.core.api.RequestDefinition;
import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.util.ResourceReader;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
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
    private String reportingString;

    public HttpRequestDefinition(String url,
                                 String method,
                                 String timeout,
                                 Map<String, String> body,
                                 Map<String, String> headers,
                                 Auth auth,
                                 String reportingString) {
        this.url = url;
        this.method = method;
        this.timeout = timeout;
        this.body = body;
        this.headers = headers;
        this.auth = auth;
        this.reportingString = reportingString;
        prepare();
    }

    /**
     * {@code reportingString} defaults to {@code null} (not yet an interpolated copy).
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

    @Override
    public HttpRequestDefinition interpolated(Interpolation interpolation, RuntimeData runtimeData) {
        if (reportingString != null) {
            throw new BratException(String.format("This RequestDefinition (%s) is already an interpolated copy", this));
        }
        var urlOutcome = interpolation.outcome(url, runtimeData);
        var methodOutcome = interpolation.outcome(method, runtimeData);
        var timeoutOutcome = timeout == null ? null : interpolation.outcome(timeout, runtimeData);
        var bodyOutcomes = interpolateMapWithOutcomes(interpolation, runtimeData, body);
        var headerOutcomes = interpolateMapWithOutcomes(interpolation, runtimeData, headers);
        var interpolatedAuth = auth.interpolated(interpolation, runtimeData);

        var report = new StringBuilder("url: ").append(urlOutcome.reportingString());
        report.append(", method: ").append(methodOutcome.reportingString());
        if (timeoutOutcome != null) {
            report.append(", timeout: ").append(timeoutOutcome.reportingString());
        }
        bodyOutcomes.forEach((key, outcome) -> report.append(", body.").append(key).append(": ").append(outcome.reportingString()));
        headerOutcomes.forEach((key, outcome) -> report.append(", header.").append(key).append(": ").append(outcome.reportingString()));
        report.append(", auth: ").append(interpolatedAuth.getReportingString());

        var resolvedBody = new LinkedHashMap<String, String>();
        bodyOutcomes.forEach((key, outcome) -> resolvedBody.put(key, outcome.asString()));
        var resolvedHeaders = new LinkedHashMap<String, String>();
        headerOutcomes.forEach((key, outcome) -> resolvedHeaders.put(key, outcome.asString()));

        return new HttpRequestDefinition(
                urlOutcome.asString(),
                methodOutcome.asString(),
                timeoutOutcome == null ? null : timeoutOutcome.asString(),
                resolvedBody,
                resolvedHeaders,
                interpolatedAuth,
                report.toString());
    }

}
