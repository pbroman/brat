package dev.pbroman.brat.core.handler;

import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.data.result.HttpResponse;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

import static dev.pbroman.brat.core.util.Constants.BODY;
import static dev.pbroman.brat.core.util.Constants.HEADERS;
import static dev.pbroman.brat.core.util.Constants.JSON;
import static dev.pbroman.brat.core.util.Constants.STATUS_CODE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpResponseVarsTest {

    @Test
    void of_holdsTheStatusCodeAsANumber() {
        // given
        var response = new HttpResponse(201, Map.of(), null);

        // when
        var vars = HttpResponseVars.of(response);

        // then - the rules call toString on it, so the type only has to render as the author wrote it
        assertThat(vars).containsEntry(STATUS_CODE, 201);
    }

    @Test
    void of_holdsTheBodyVerbatim() {
        // given
        var response = new HttpResponse(200, Map.of(), "  not trimmed  ");

        // when
        var vars = HttpResponseVars.of(response);

        // then
        assertThat(vars).containsEntry(BODY, "  not trimmed  ");
    }

    @Test
    void of_flattensEachHeaderToItsFirstValue() {
        // given - Set-Cookie is the everyday repeated header
        var response = new HttpResponse(200, Map.of("Set-Cookie", List.of("a=1", "b=2")), null);

        // when
        var vars = HttpResponseVars.of(response);

        // then - ${response.headers.Set-Cookie} substitutes one string; the full list stays on HttpResponse
        assertThat(headersOf(vars)).containsEntry("Set-Cookie", "a=1");
    }

    @Test
    void of_looksUpAHeaderWhateverCaseItWasSentIn() {
        // given - some servers send content-type, others Content-Type
        var response = new HttpResponse(200, Map.of("Content-Type", List.of("application/json")), null);

        // when
        var vars = HttpResponseVars.of(response);

        // then - an author writing ${response.headers.content-type} must not depend on the server's choice
        assertThat(headersOf(vars)).containsEntry("content-type", "application/json");
    }

    @Test
    void of_holdsAnEmptyHeaderMapWhenThereAreNoHeaders() {
        // given
        var response = new HttpResponse(204, Map.of(), null);

        // when
        var vars = HttpResponseVars.of(response);

        // then - present but empty, since a rule checks the key before iterating it
        assertThat(vars).containsKey(HEADERS);
        assertThat(headersOf(vars)).isEmpty();
    }

    @Test
    void of_holdsTheBodyUnderJsonWhenItParsesAsAnObject() {
        // given
        var response = new HttpResponse(200, Map.of(), "{\"id\": \"123\"}");

        // when
        var vars = HttpResponseVars.of(response);

        // then - the string itself, since every consumer feeds toString() to JSONPath
        assertThat(vars).containsEntry(JSON, "{\"id\": \"123\"}");
    }

    @Test
    void of_holdsTheBodyUnderJsonWhenItParsesAsAnArray() {
        // given - a collection endpoint returns a top-level array
        var response = new HttpResponse(200, Map.of(), "[{\"id\": \"1\"}]");

        // when
        var vars = HttpResponseVars.of(response);

        // then
        assertThat(vars).containsEntry(JSON, "[{\"id\": \"1\"}]");
    }

    @Test
    void of_omitsJsonWhenTheBodyIsNotJson() {
        // given
        var response = new HttpResponse(200, Map.of(), "<html>not json</html>");

        // when
        var vars = HttpResponseVars.of(response);

        // then - absence is how a rule tells "no JSON here" from "JSON with nothing at that path"
        assertThat(vars).containsEntry(BODY, "<html>not json</html>").doesNotContainKey(JSON);
    }

    @Test
    void of_omitsJsonWhenTheBodyIsARejectedButValidJsonScalar() {
        // given - each of these is a valid JSON document that readTree parses happily
        for (var scalar : new String[] {"42", "true", "null", "\"just a string\""}) {
            // when
            var vars = HttpResponseVars.of(new HttpResponse(200, Map.of(), scalar));

            // then - a scalar under json would only move its failure inside JSONPath
            assertThat(vars).as("body %s", scalar).containsEntry(BODY, scalar).doesNotContainKey(JSON);
        }
    }

    @Test
    void of_omitsJsonWhenTheBodyIsEmptyOrBlank() {
        // given - readTree answers both with a MissingNode rather than throwing, so neither reaches
        // the catch that would otherwise have excluded them
        for (var blank : new String[] {"", "   ", "\n"}) {
            // when
            var vars = HttpResponseVars.of(new HttpResponse(200, Map.of(), blank));

            // then
            assertThat(vars)
                    .as("body %s", blank.isEmpty() ? "<empty>" : "<blank>")
                    .doesNotContainKey(JSON);
        }
    }

    @Test
    void of_holdsJsonForAnEmptyObjectOrArray() {
        // given - empty is not the same as absent; both are containers an assertion may address
        // then
        assertThat(HttpResponseVars.of(new HttpResponse(200, Map.of(), "{}"))).containsEntry(JSON, "{}");
        assertThat(HttpResponseVars.of(new HttpResponse(200, Map.of(), "[]"))).containsEntry(JSON, "[]");
    }

    @Test
    void of_omitsTheBodyAndJsonWhenTheResponseHasNone() {
        // given
        var response = new HttpResponse(204, Map.of(), null);

        // when
        var vars = HttpResponseVars.of(response);

        // then
        assertThat(vars).doesNotContainKey(BODY).doesNotContainKey(JSON);
    }

    @Test
    void of_returnsAnUnmodifiableMap() {
        // given
        var vars = HttpResponseVars.of(new HttpResponse(200, Map.of("Content-Type", List.of("text/plain")), null));

        // then - responseVars is rebuilt per response, never accumulated into
        assertThatThrownBy(() -> vars.put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void of_returnsAnUnmodifiableHeaderMap() {
        // given - the nested map is the one a caller actually holds on to
        var headers = headersOf(
                HttpResponseVars.of(new HttpResponse(200, Map.of("Content-Type", List.of("text/plain")), null)));

        // then
        assertThatThrownBy(() -> headers.put("X-Added", "y")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void of_throwsForANullResponse() {
        assertThatThrownBy(() -> HttpResponseVars.of(null)).isInstanceOf(BratException.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> headersOf(Map<String, Object> vars) {
        return (Map<String, String>) vars.get(HEADERS);
    }
}
