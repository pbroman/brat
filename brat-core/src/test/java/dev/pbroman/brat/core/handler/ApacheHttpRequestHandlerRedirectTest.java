package dev.pbroman.brat.core.handler;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.data.Auth;
import dev.pbroman.brat.core.data.HttpBody;
import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.data.RequestOptions;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * How {@link ApacheHttpRequestHandler} treats a redirect: not followed by default, followed when the
 * options say so, and refused across origins when the request carries credentials. Two stub servers
 * on two ports are two origins.
 */
class ApacheHttpRequestHandlerRedirectTest {

    private static final RequestOptions DEFAULT = new RequestOptions(null);
    private static final RequestOptions FOLLOW = new RequestOptions(null, "true", null, null);

    private StubHttpServer stub;
    private StubHttpServer other;
    private ApacheHttpRequestHandler underTest;

    @BeforeEach
    void setUp() {
        stub = new StubHttpServer();
        other = new StubHttpServer();
        underTest = new ApacheHttpRequestHandler();
    }

    @AfterEach
    void tearDown() {
        underTest.close();
        stub.close();
        other.close();
    }

    private HttpRequestDefinition get(String path, Map<String, String> headers) {
        return new HttpRequestDefinition(stub.baseUrl() + path, "GET", null, headers);
    }

    // --- not following ---

    @Test
    void performRequest_returnsTheRedirectItselfByDefault() {
        // given
        stub.redirect("/old", 302, stub.baseUrl() + "/new");

        // when
        var response = underTest.performRequest(get("/old", null), DEFAULT);

        // then
        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().get("Location")).containsExactly(stub.baseUrl() + "/new");
        assertThat(response.redirectNotFollowed()).isNull();
        assertThat(stub.requestCount()).isEqualTo(1);
    }

    // --- following ---

    @Test
    void performRequest_followsASameOriginRedirectWhenAsked() {
        // given
        stub.redirect("/old", 302, "/new");
        stub.respond(200, "arrived");

        // when
        var response = underTest.performRequest(get("/old", Map.of("Authorization", "Bearer abcd")), FOLLOW);

        // then - credentials do not stop a redirect that stays on the origin
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("arrived");
        assertThat(response.redirectNotFollowed()).isNull();
        assertThat(stub.lastRequest().path()).isEqualTo("/new");
    }

    @Test
    void performRequest_resendsAPostAnswered302AsAGetWithoutItsBody() {
        // given
        stub.redirect("/old", 302, "/new");
        var post = new HttpRequestDefinition(stub.baseUrl() + "/old", "POST", HttpBody.raw("{\"a\": 1}"), null);

        // when
        underTest.performRequest(post, FOLLOW);

        // then
        assertThat(stub.lastRequest().method()).isEqualTo("GET");
        assertThat(stub.lastRequest().body()).isEmpty();
    }

    @Test
    void performRequest_keepsMethodAndBodyOn307() {
        // given
        stub.redirect("/old", 307, "/new");
        var post = new HttpRequestDefinition(stub.baseUrl() + "/old", "POST", HttpBody.raw("{\"a\": 1}"), null);

        // when
        underTest.performRequest(post, FOLLOW);

        // then
        assertThat(stub.lastRequest().method()).isEqualTo("POST");
        assertThat(stub.lastRequest().body()).isEqualTo("{\"a\": 1}");
    }

    @Test
    void performRequest_followsAnotherOriginWhenTheRequestCarriesNoCredentials() {
        // given
        stub.redirect("/old", 302, other.baseUrl() + "/new");
        other.respond(200, "elsewhere");

        // when
        var response = underTest.performRequest(get("/old", null), FOLLOW);

        // then
        assertThat(response.body()).isEqualTo("elsewhere");
        assertThat(response.redirectNotFollowed()).isNull();
    }

    @Test
    void performRequest_throwsOnARedirectLoop() {
        // given
        stub.redirect("/a", 302, "/b");
        stub.redirect("/b", 302, "/a");

        // when / then
        assertThatThrownBy(() -> underTest.performRequest(get("/a", null), FOLLOW))
                .isInstanceOf(BratException.class);
    }

    // --- refusing ---

    @Test
    void performRequest_refusesAnotherOriginForAnAuthorizationHeaderAndSaysWhere() {
        // given
        var location = other.baseUrl() + "/new";
        stub.redirect("/old", 302, location);

        // when
        var response = underTest.performRequest(get("/old", Map.of("Authorization", "Bearer abcd")), FOLLOW);

        // then - the 3xx comes back, and the other origin never saw the credential
        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.redirectNotFollowed()).isEqualTo(location);
        assertThat(other.requestCount()).isZero();
    }

    @Test
    void performRequest_refusesAnotherOriginForAnAuthThatIsNotNone() {
        // given - an apikey header, which the client's own rule does not know
        stub.redirect("/old", 302, other.baseUrl() + "/new");
        var options = new RequestOptions(null, "true", new Auth("apikey", Map.of("value", "k")), null);

        // when
        var response = underTest.performRequest(get("/old", Map.of("X-Api-Key", "k-12345")), options);

        // then
        assertThat(response.redirectNotFollowed()).isNotNull();
        assertThat(other.requestCount()).isZero();
    }

    @Test
    void performRequest_refusesAnotherOriginForAHeaderBuiltFromASecret() {
        // given
        stub.redirect("/old", 302, other.baseUrl() + "/new");
        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();
        outcomes.put("header.X-Token", new InterpolationOutcome("t-12345", "${secrets.t} → ***", true));
        var definition = new HttpRequestDefinition(
                stub.baseUrl() + "/old", "GET", null, Map.of("X-Token", "t-12345"), null, outcomes);

        // when
        var response = underTest.performRequest(definition, FOLLOW);

        // then
        assertThat(response.redirectNotFollowed()).isNotNull();
        assertThat(other.requestCount()).isZero();
    }

    @Test
    void performRequest_refusesA307ToAnotherOriginWhenTheBodyHoldsASecret() {
        // given - a token request: a 307 would post the client secret to the other origin
        stub.redirect("/token", 307, other.baseUrl() + "/token");
        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();
        outcomes.put("body.form.secret", new InterpolationOutcome("s-12345", "${secrets.s} → ***", true));
        var definition = new HttpRequestDefinition(
                stub.baseUrl() + "/token", "POST", HttpBody.form(Map.of("secret", "s-12345")), null, null, outcomes);

        // when
        var response = underTest.performRequest(definition, FOLLOW);

        // then
        assertThat(response.statusCode()).isEqualTo(307);
        assertThat(other.requestCount()).isZero();
    }

    @Test
    void performRequest_recordsNoRefusalForARedirectStatusWithoutALocation() {
        // given - a 302 that names nowhere: nothing to follow, so nothing was refused
        stub.respond(302, null);

        // when
        var response = underTest.performRequest(get("/old", null), FOLLOW);

        // then
        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.redirectNotFollowed()).isNull();
    }
}
