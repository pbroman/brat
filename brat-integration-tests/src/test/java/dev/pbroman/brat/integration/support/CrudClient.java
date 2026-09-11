package dev.pbroman.brat.integration.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Puts the server into the state a test needs, and reads it back — without going through BRAT.
 *
 * <p><strong>Why not seed with BRAT requests.</strong> A fixture built out of the thing under test
 * reports its own bugs as failures of whatever the test was actually about, and every suite grows a
 * preamble that is not its subject. So a test's <em>given</em> runs through this client and its
 * <em>when</em> runs through BRAT.
 *
 * <p>The one deliberate exception is a test whose subject <em>is</em> the chain — a capture feeding
 * the next request — where the seeding requests have to be BRAT's, because that is the claim.
 */
public final class CrudClient {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /**
     * One client for the whole module run. {@code HttpClient} is thread-safe and holds a selector
     * thread and an executor, and JUnit builds a new test instance per test method — so a field here
     * would leave one of each behind per test.
     */
    private static final HttpClient HTTP =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private final String baseUrl;

    /**
     * @param baseUrl the running application's base URL, with no trailing slash
     */
    public CrudClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /** Empties users and jobs, so a test starts from a known database. */
    public void clear() {
        send(HttpRequest.newBuilder(uri("/clear")).DELETE());
    }

    /**
     * Creates a user from a JSON body.
     *
     * @param json the user to create, as the API expects it
     * @return the created user, including the server-assigned {@code id} and {@code createdAt}
     */
    public JsonNode create(String json) {
        return json(send(HttpRequest.newBuilder(uri("/create"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))));
    }

    /**
     * Creates a user with nothing but a username — the cheapest seed there is.
     *
     * @param username the username to create
     * @return the created user
     */
    public JsonNode createUser(String username) {
        return create("{\"username\": \"" + username + "\"}");
    }

    /**
     * Starts a polling job.
     *
     * @param readyAfter how many polls report {@code pending} before the first {@code ready}
     * @return the job, never yet polled
     */
    public JsonNode startJob(int readyAfter) {
        return json(send(HttpRequest.newBuilder(uri("/job?readyAfter=" + readyAfter))
                .POST(HttpRequest.BodyPublishers.noBody())));
    }

    /**
     * Reads any path on the server, for a test that needs to check the server's state afterwards.
     *
     * @param path the path to read, starting with {@code /}
     * @return the response body as JSON
     */
    public JsonNode get(String path) {
        return json(send(HttpRequest.newBuilder(uri(path)).GET()));
    }

    private URI uri(String path) {
        return URI.create(baseUrl + path);
    }

    private HttpResponse<String> send(HttpRequest.Builder builder) {
        // Built once, so the messages below report the request that was actually sent rather than a
        // second one built from a builder that may since have moved on.
        var request = builder.build();
        try {
            var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException(
                        "The fixture call to " + request.uri() + " answered " + response.statusCode());
            }
            return response;
        } catch (IOException e) {
            throw new IllegalStateException("The fixture call to " + request.uri() + " failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("The fixture call to " + request.uri() + " was interrupted", e);
        }
    }

    private static JsonNode json(HttpResponse<String> response) {
        return MAPPER.readTree(response.body());
    }
}
