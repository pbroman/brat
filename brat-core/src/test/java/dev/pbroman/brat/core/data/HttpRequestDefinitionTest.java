package dev.pbroman.brat.core.data;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpRequestDefinitionTest {

    private static final String JSON = "application/json";
    private static final String FORM = "application/x-www-form-urlencoded";

    @Test
    void constructor_keepsTheBodyItWasGiven() {
        // given
        var body = HttpBody.raw("{\"item\": \"widget\"}");

        // when
        var definition = new HttpRequestDefinition("http://x", "POST", body, Map.of("Content-Type", JSON));

        // then
        assertThat(definition.getBody()).isSameAs(body);
    }

    @Test
    void constructor_doesNotThrowForABodyWithNullHeaders() {
        // when / then — the constructor's own Javadoc permits null headers
        assertThatCode(() -> new HttpRequestDefinition("http://x", "POST", HttpBody.form(Map.of("a", "1")), null))
                .doesNotThrowAnyException();
    }

    @Test
    void constructor_doesNotReadAFileBody() {
        // given — a path that does not exist; the read happens at request time, after interpolation
        var body = HttpBody.file("bodies/does-not-exist-${vars.stage}.json");

        // when
        var definition = new HttpRequestDefinition("http://x", "POST", body, Map.of());

        // then
        assertThat(definition.getBody().getFileContent()).isNull();
    }

    // --- a form and its Content-Type ---

    @Test
    void constructor_acceptsAFormWithoutAContentType() {
        // when
        var definition = new HttpRequestDefinition("http://x", "POST", HttpBody.form(Map.of("a", "1")), Map.of());

        // then - interpolation adds it, construction does not
        assertThat(definition.getHeaders()).isEmpty();
    }

    @Test
    void constructor_acceptsAFormWithTheFormContentTypeInAnyCaseAndWithParameters() {
        // given
        var body = HttpBody.form(Map.of("a", "1"));

        // when / then
        assertThatCode(() -> new HttpRequestDefinition("http://x", "POST", body, Map.of("Content-Type", FORM)))
                .doesNotThrowAnyException();
        assertThatCode(() -> new HttpRequestDefinition(
                        "http://x", "POST", body, Map.of("content-type", "Application/X-WWW-Form-Urlencoded")))
                .doesNotThrowAnyException();
        assertThatCode(() -> new HttpRequestDefinition(
                        "http://x", "POST", body, Map.of("Content-Type", FORM + "; charset=UTF-8")))
                .doesNotThrowAnyException();
    }

    @Test
    void constructor_rejectsAFormUnderAnotherContentType() {
        // given
        var body = HttpBody.form(Map.of("a", "1"));

        // when / then
        assertThatThrownBy(() -> new HttpRequestDefinition("http://x", "POST", body, Map.of("Content-Type", JSON)))
                .isInstanceOf(BratException.class)
                .hasMessageContaining(JSON);
    }

    @Test
    void constructor_leavesAContentTypeHoldingATokenForTheInterpolatedCopy() {
        // given
        var body = HttpBody.form(Map.of("a", "1"));

        // when / then - it may resolve to the form type, so it cannot be judged yet
        assertThatCode(() -> new HttpRequestDefinition(
                        "http://x", "POST", body, Map.of("Content-Type", "${env.contentType}")))
                .doesNotThrowAnyException();
    }

    @Test
    void constructor_acceptsAnyContentTypeForABodyThatIsNotAForm() {
        // when / then
        assertThatCode(() -> new HttpRequestDefinition(
                        "http://x", "POST", HttpBody.raw("a=1"), Map.of("Content-Type", FORM)))
                .doesNotThrowAnyException();
        assertThatCode(() -> new HttpRequestDefinition(
                        "http://x", "POST", HttpBody.file("bodies/order.json"), Map.of("Content-Type", JSON)))
                .doesNotThrowAnyException();
    }

    @Test
    void constructor_keepsHeadersExactlyAsAuthored() {
        // given
        var headers = Map.of("content-type", JSON);

        // when
        var definition = new HttpRequestDefinition("http://x", "POST", null, headers);

        // then — the author's spelling is what a report and an outcome key echo
        assertThat(definition.getHeaders()).containsOnlyKeys("content-type");
    }

    @Test
    void constructor_defaultsAnAbsentMethodToGet() {
        // when - a null method would otherwise bind fine and fail at request time
        var definition = new HttpRequestDefinition("http://x", null, null, null);

        // then
        assertThat(definition.getMethod()).isEqualTo("GET");
    }

    @Test
    void constructor_keepsADeclaredMethod() {
        // when
        var definition = new HttpRequestDefinition("http://x", "POST", null, null);

        // then
        assertThat(definition.getMethod()).isEqualTo("POST");
    }

    @Test
    void constructor_keepsANullBodyNull() {
        // when
        var definition = new HttpRequestDefinition("http://x", "GET", null, Map.of());

        // then
        assertThat(definition.getBody()).isNull();
    }

    @Test
    void constructor_rejectsHeaderNamesThatDifferOnlyInCase() {
        // given - two YAML keys, one HTTP header, two contradictory values
        var headers = new LinkedHashMap<String, String>();
        headers.put("Content-Type", JSON);
        headers.put("content-type", FORM);

        // then - rejected at construction, so it fails whether or not anything reads that header
        assertThatThrownBy(() -> new HttpRequestDefinition("http://x", "POST", null, headers))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("Content-Type");
    }

    @Test
    void constructor_defaultsSeverityAndDropsNoHeadersWhenBothAreNull() {
        // when
        var definition = new HttpRequestDefinition("http://x", "GET", null, null);

        // then
        assertThat(definition.getHeaders()).isNull();
        assertThat(definition.getBody()).isNull();
    }

    @Test
    void protocol_isHttpAndComesFromTheTypeRatherThanTheDocument() {
        // given - the authored protocol: key selects which class binds and is consumed doing so
        var definition = new HttpRequestDefinition("http://x/y", "GET", null, null);

        // then
        assertThat(definition.protocol()).isEqualTo("http");
    }

    @Test
    void args_defaultToAnEmptyMapRatherThanNull() {
        // given - every handler has to validate the bag, so none of them should have to null-check it
        var definition = new HttpRequestDefinition("http://x/y", "GET", null, null);

        // then
        assertThat(definition.getArgs()).isEmpty();
    }

    @Test
    void args_areCopiedAndUnmodifiable() {
        // given
        var authored = new LinkedHashMap<String, String>();
        authored.put("certAlias", "client-a");
        var definition = new HttpRequestDefinition("http://x/y", "GET", null, null, authored);

        // when - the author's map goes on living after the definition is built
        authored.put("added", "later");

        // then
        assertThat(definition.getArgs()).containsExactly(java.util.Map.entry("certAlias", "client-a"));
        assertThatThrownBy(() -> definition.getArgs().put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
    }
}
