package dev.pbroman.brat.core.data;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpBodyTest {

    private static Map<String, String> form(String... keysAndValues) {
        var form = new LinkedHashMap<String, String>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            form.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return form;
    }

    // --- exactly one kind ---

    @Test
    void constructor_acceptsEachKindAlone() {
        // when
        var raw = new HttpBody("{}", null, null);
        var file = new HttpBody(null, "bodies/order.json", null);
        var form = new HttpBody(null, null, Map.of("a", "1"));

        // then
        assertThat(raw.getRaw()).isEqualTo("{}");
        assertThat(file.getFile()).isEqualTo("bodies/order.json");
        assertThat(form.getForm()).containsExactly(Map.entry("a", "1"));
    }

    @Test
    void constructor_rejectsABodyDeclaringNothing() {
        // when / then - `body: {}`
        assertThatThrownBy(() -> new HttpBody(null, null, null))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("none of raw, file or form");
    }

    @Test
    void constructor_rejectsTwoKindsNamingBothAndQuotingNeither() {
        // when / then
        assertThatThrownBy(() -> new HttpBody("{\"secret\": 1}", null, Map.of("a", "s3cr3t")))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("raw and form")
                .hasMessageNotContaining("secret")
                .hasMessageNotContaining("s3cr3t");
        assertThatThrownBy(() -> new HttpBody("{}", "bodies/order.json", null))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("raw and file")
                .hasMessageNotContaining("order.json");
    }

    @Test
    void constructor_rejectsAllThreeKinds() {
        // when / then
        assertThatThrownBy(() -> new HttpBody("{}", "bodies/order.json", Map.of()))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("raw and file and form");
    }

    // --- form entries ---

    @Test
    void constructor_rejectsAFormEntryWithoutAValueNamingIt() {
        // given - `client_id:` with nothing after it
        var form = new HashMap<String, String>();
        form.put("client_id", null);

        // when / then
        assertThatThrownBy(() -> HttpBody.form(form))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("client_id");
    }

    @Test
    void constructor_rejectsAFormEntryWithoutAName() {
        // given
        var form = new HashMap<String, String>();
        form.put(null, "x");

        // when / then
        assertThatThrownBy(() -> HttpBody.form(form)).isInstanceOf(BratException.class);
    }

    @Test
    void constructor_acceptsAnEmptyForm() {
        // when / then
        assertThat(HttpBody.form(Map.of()).getForm()).isEmpty();
    }

    @Test
    void constructor_copiesTheFormKeepingItsOrder() {
        // given
        var form = form("z", "1", "a", "2", "m", "3");

        // when
        var body = HttpBody.form(form);
        form.put("added", "later");

        // then
        assertThat(body.getForm()).containsExactly(Map.entry("z", "1"), Map.entry("a", "2"), Map.entry("m", "3"));
    }

    @Test
    void getForm_isUnmodifiable() {
        // given
        var body = HttpBody.form(form("a", "1"));

        // when / then
        assertThatThrownBy(() -> body.getForm().put("b", "2")).isInstanceOf(UnsupportedOperationException.class);
    }

    // --- file content ---

    @Test
    void constructor_carriesTheContentOfAFileBody() {
        // when
        var body = new HttpBody(null, "bodies/order.json", null, "{\"id\": 1}");

        // then
        assertThat(body.getFileContent()).isEqualTo("{\"id\": 1}");
    }

    @Test
    void constructor_rejectsFileContentForABodyThatIsNotAFile() {
        // when / then
        assertThatThrownBy(() -> new HttpBody("{}", null, null, "content")).isInstanceOf(BratException.class);
    }

    // --- factories ---

    @Test
    void factories_buildTheirKind() {
        // when / then
        assertThat(HttpBody.raw("{}").getRaw()).isEqualTo("{}");
        assertThat(HttpBody.file("bodies/order.json").getFile()).isEqualTo("bodies/order.json");
        assertThat(HttpBody.form(Map.of("a", "1")).getForm()).containsEntry("a", "1");
    }

    @Test
    void factories_rejectNull() {
        // when / then
        assertThatThrownBy(() -> HttpBody.raw(null)).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> HttpBody.file(null)).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> HttpBody.form(null)).isInstanceOf(BratException.class);
    }

    // --- getPayload ---

    @Test
    void getPayload_isARawBodyAsItIs() {
        // when / then
        assertThat(HttpBody.raw("a=1&b=${vars.x}").getPayload()).isEqualTo("a=1&b=${vars.x}");
    }

    @Test
    void getPayload_isNullForAFileNotYetRead() {
        // when / then
        assertThat(HttpBody.file("bodies/order.json").getPayload()).isNull();
    }

    @Test
    void getPayload_isTheContentOfAFileThatWasRead() {
        // when / then
        assertThat(new HttpBody(null, "bodies/order.json", null, "{\"id\": 1}").getPayload())
                .isEqualTo("{\"id\": 1}");
    }

    @Test
    void getPayload_joinsAFormInTheOrderWritten() {
        // when / then
        assertThat(HttpBody.form(form("grant_type", "client_credentials", "client_id", "orders"))
                        .getPayload())
                .isEqualTo("grant_type=client_credentials&client_id=orders");
    }

    @Test
    void getPayload_encodesKeysAndValues() {
        // given - every character that would otherwise corrupt the body or the server's reading of it
        var body = HttpBody.form(form("a b", "x&y=z", "näme", "grün 100%", "plus", "1+1"));

        // when / then
        assertThat(body.getPayload()).isEqualTo("a+b=x%26y%3Dz&n%C3%A4me=gr%C3%BCn+100%25&plus=1%2B1");
    }

    @Test
    void getPayload_sendsAnEmptyValueAsKeyEquals() {
        // when / then
        assertThat(HttpBody.form(form("client_id", "", "scope", "read")).getPayload())
                .isEqualTo("client_id=&scope=read");
    }

    @Test
    void getPayload_isEmptyForAnEmptyForm() {
        // when / then
        assertThat(HttpBody.form(Map.of()).getPayload()).isEmpty();
    }
}
