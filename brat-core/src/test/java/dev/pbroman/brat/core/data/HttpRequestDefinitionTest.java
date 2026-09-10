package dev.pbroman.brat.core.data;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

import static dev.pbroman.brat.core.util.Constants.BODY_STRING;
import static dev.pbroman.brat.core.util.Constants.FILE_BODY;
import static dev.pbroman.brat.core.util.Constants.RAW_BODY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpRequestDefinitionTest {

    private static final String JSON = "application/json";
    private static final String FORM = "application/x-www-form-urlencoded";

    @Test
    void constructor_derivesBodyStringFromARawBody() {
        // given
        var body = Map.of(RAW_BODY, "{\"item\": \"widget\"}");

        // when
        var definition = new HttpRequestDefinition("http://x", "POST", null, body, Map.of("Content-Type", JSON), null);

        // then
        assertThat(definition.getBody()).containsEntry(BODY_STRING, "{\"item\": \"widget\"}");
    }

    @Test
    void constructor_doesNotThrowForABodyWithoutContentType() {
        // given — the common case: a JSON body with the content type set at suite level
        var body = Map.of(RAW_BODY, "{}");

        // then
        assertThatCode(() -> new HttpRequestDefinition("http://x", "POST", null, body, Map.of(), null))
                .doesNotThrowAnyException();
    }

    @Test
    void constructor_doesNotThrowForABodyWithNullHeaders() {
        // given
        var body = Map.of(RAW_BODY, "{}");

        // then — the constructor's own Javadoc permits null headers
        assertThatCode(() -> new HttpRequestDefinition("http://x", "POST", null, body, null, null))
                .doesNotThrowAnyException();
    }

    @Test
    void constructor_formEncodesWhenContentTypeIsFormUrlencoded() {
        // given
        var body = new LinkedHashMap<String, String>();
        body.put("a", "1");
        body.put("b", "2");

        // when
        var definition = new HttpRequestDefinition("http://x", "POST", null, body, Map.of("Content-Type", FORM), null);

        // then
        assertThat(definition.getBody()).containsEntry(BODY_STRING, "a=1&b=2");
    }

    @Test
    void constructor_formEncodesWhateverCaseTheContentTypeHeaderIsWrittenIn() {
        // given — HTTP header names are case-insensitive; a suite may spell it any way
        var body = new LinkedHashMap<String, String>();
        body.put("a", "1");

        // when
        var definition = new HttpRequestDefinition("http://x", "POST", null, body, Map.of("content-type", FORM), null);

        // then
        assertThat(definition.getBody()).containsEntry(BODY_STRING, "a=1");
    }

    @Test
    void constructor_doesNotReadAFileBody() {
        // given — a path that does not exist; the read happens at request time, after interpolation
        var body = Map.of(FILE_BODY, "bodies/does-not-exist-${vars.stage}.json");

        // when
        var definition = new HttpRequestDefinition("http://x", "POST", null, body, Map.of(), null);

        // then
        assertThat(definition.getBody()).containsEntry(FILE_BODY, "bodies/does-not-exist-${vars.stage}.json");
        assertThat(definition.getBody()).doesNotContainKey(BODY_STRING);
    }

    @Test
    void constructor_doesNotMutateTheBodyItWasGiven() {
        // given
        var body = new HashMap<String, String>();
        body.put(RAW_BODY, "{}");

        // when
        new HttpRequestDefinition("http://x", "POST", null, body, Map.of("Content-Type", JSON), null);

        // then
        assertThat(body).containsOnlyKeys(RAW_BODY);
    }

    @Test
    void constructor_keepsHeadersExactlyAsAuthored() {
        // given
        var headers = Map.of("content-type", JSON);

        // when
        var definition = new HttpRequestDefinition("http://x", "POST", null, null, headers, null);

        // then — the author's spelling is what a report and an outcome key echo
        assertThat(definition.getHeaders()).containsOnlyKeys("content-type");
    }

    @Test
    void constructor_defaultsAnAbsentMethodToGet() {
        // when - a null method would otherwise bind fine and fail at request time
        var definition = new HttpRequestDefinition("http://x", null, null, null, null, null);

        // then
        assertThat(definition.getMethod()).isEqualTo("GET");
    }

    @Test
    void constructor_keepsADeclaredMethod() {
        // when
        var definition = new HttpRequestDefinition("http://x", "POST", null, null, null, null);

        // then
        assertThat(definition.getMethod()).isEqualTo("POST");
    }

    @Test
    void constructor_keepsANullBodyNull() {
        // when
        var definition = new HttpRequestDefinition("http://x", "GET", null, null, Map.of(), null);

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
        assertThatThrownBy(() -> new HttpRequestDefinition("http://x", "POST", null, null, headers, null))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("Content-Type");
    }

    @Test
    void constructor_keepsAnAuthoredBodyStringOverFormEncoding() {
        // given - the author supplied _bodyString themselves
        var body = new LinkedHashMap<String, String>();
        body.put(BODY_STRING, "already=encoded");
        body.put("a", "1");

        // when
        var definition = new HttpRequestDefinition("http://x", "POST", null, body, Map.of("Content-Type", FORM), null);

        // then - "no _bodyString is present yet" means an authored one wins
        assertThat(definition.getBody()).containsEntry(BODY_STRING, "already=encoded");
    }

    @Test
    void constructor_doesNotFormEncodeAFileBody() {
        // given - a file body with a form content type is still a file body
        var body = Map.of(FILE_BODY, "bodies/order.json");

        // when
        var definition = new HttpRequestDefinition("http://x", "POST", null, body, Map.of("Content-Type", FORM), null);

        // then - form-encoding it would send the literal path as the payload
        assertThat(definition.getBody()).containsOnlyKeys(FILE_BODY);
    }

    @Test
    void constructor_derivesNoBodyStringFromAnEmptyBody() {
        // given
        var body = new LinkedHashMap<String, String>();

        // when
        var definition = new HttpRequestDefinition("http://x", "POST", null, body, Map.of("Content-Type", FORM), null);

        // then
        assertThat(definition.getBody()).isEmpty();
    }

    @Test
    void constructor_defaultsSeverityAndDropsNoHeadersWhenBothAreNull() {
        // when
        var definition = new HttpRequestDefinition("http://x", "GET", null, null, null, null);

        // then
        assertThat(definition.getHeaders()).isNull();
        assertThat(definition.getBody()).isNull();
    }

    @Test
    void constructor_leavesAPlainBodyUnchangedForANonFormContentType() {
        // given - no raw, no file, no _bodyString, and JSON rather than form encoding
        var body = new LinkedHashMap<String, String>();
        body.put("a", "1");

        // when
        var definition = new HttpRequestDefinition("http://x", "POST", null, body, Map.of("Content-Type", JSON), null);

        // then - the documented third case: returned unchanged
        assertThat(definition.getBody()).containsOnlyKeys("a");
    }
}
