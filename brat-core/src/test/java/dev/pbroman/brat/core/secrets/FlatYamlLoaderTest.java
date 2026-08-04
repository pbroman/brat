package dev.pbroman.brat.core.secrets;

import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

class FlatYamlLoaderTest {

    @Test
    void load_returnsTheDocumentsEntries() {
        // given
        var yaml = """
                apiKey: s3cret
                dbToken: t0ken
                """;

        // when
        var result = FlatYamlLoader.load(yaml);

        // then
        assertThat(result).containsOnly(entry("apiKey", "s3cret"), entry("dbToken", "t0ken"));
    }

    @Test
    void load_flattensNestedMappingsWithDots() {
        // given
        var yaml = """
                db:
                  password: s3cret
                  username: me
                """;

        // when
        var result = FlatYamlLoader.load(yaml);

        // then
        assertThat(result).containsOnly(entry("db.password", "s3cret"), entry("db.username", "me"));
    }

    @Test
    void load_flattensNestedMappingsToAnyDepth() {
        // given
        var yaml = """
                a:
                  b:
                    c:
                      d: deep
                """;

        // when
        var result = FlatYamlLoader.load(yaml);

        // then
        assertThat(result).containsOnly(entry("a.b.c.d", "deep"));
    }

    @Test
    void load_preservesScalarSourceTextWithoutTypeInference() {
        // given
        var yaml = """
                float: 1.50
                leadingZero: 0123
                bool: true
                integer: 12345
                infinity: .inf
                """;

        // when
        var result = FlatYamlLoader.load(yaml);

        // then
        assertThat(result)
                .containsOnly(
                        entry("float", "1.50"),
                        entry("leadingZero", "0123"),
                        entry("bool", "true"),
                        entry("integer", "12345"),
                        entry("infinity", ".inf"));
    }

    @Test
    void load_returnsEntriesInDocumentOrder() {
        // given
        var yaml = """
                zebra: 1
                apple: 2
                mango: 3
                """;

        // when
        var result = FlatYamlLoader.load(yaml);

        // then
        assertThat(result.keySet()).containsExactly("zebra", "apple", "mango");
    }

    @Test
    void load_keepsAnEmptyStringValue() {
        // given
        var yaml = """
                apiKey: ""
                """;

        // when
        var result = FlatYamlLoader.load(yaml);

        // then
        assertThat(result).containsOnly(entry("apiKey", ""));
    }

    @Test
    void load_returnsAnEmptyMapForAnEmptyDocument() {
        // when
        var result = FlatYamlLoader.load("");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void load_returnsAnEmptyMapForABlankDocument() {
        // when
        var result = FlatYamlLoader.load("   \n\n  ");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void load_returnsAnEmptyMapForACommentsOnlyDocument() {
        // given
        var yaml = """
                # nothing but a comment
                # and another
                """;

        // when
        var result = FlatYamlLoader.load(yaml);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void load_returnsAnEmptyMapWhenEveryNestedMappingIsEmpty() {
        // given
        var yaml = """
                db: {}
                vault: {}
                """;

        // when
        var result = FlatYamlLoader.load(yaml);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void load_returnsAnUnmodifiableMap() {
        // given
        var result = FlatYamlLoader.load("apiKey: s3cret\n");

        // then
        assertThatThrownBy(() -> result.put("dbToken", "t0ken")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void load_throwsOnNullInput() {
        // then
        assertThatThrownBy(() -> FlatYamlLoader.load(null)).isInstanceOf(BratException.class);
    }

    @Test
    void load_throwsOnMalformedYaml() {
        // given
        var yaml = "apiKey: \"unterminated\n";

        // then
        assertThatThrownBy(() -> FlatYamlLoader.load(yaml)).isInstanceOf(BratException.class);
    }

    @Test
    void load_doesNotQuoteTheDocumentInAParseError() {
        // given
        var yaml = "apiKey: \"sup3rS3cretValue\n";

        // then
        assertThatThrownBy(() -> FlatYamlLoader.load(yaml))
                .isInstanceOf(BratException.class)
                .hasMessageNotContaining("sup3rS3cretValue")
                .hasNoCause();
    }

    @Test
    void load_throwsIfTheTopLevelIsNotAMapping() {
        // given
        var yaml = "justAScalar\n";

        // then
        assertThatThrownBy(() -> FlatYamlLoader.load(yaml)).isInstanceOf(BratException.class);
    }

    @Test
    void load_throwsOnASequenceValue() {
        // given
        var yaml = """
                apiKeys:
                  - one
                  - two
                """;

        // then
        assertThatThrownBy(() -> FlatYamlLoader.load(yaml)).isInstanceOf(BratException.class);
    }

    @Test
    void load_throwsOnASequenceNestedDeeperInTheDocument() {
        // given
        var yaml = """
                db:
                  passwords:
                    - one
                """;

        // then
        assertThatThrownBy(() -> FlatYamlLoader.load(yaml)).isInstanceOf(BratException.class);
    }

    @Test
    void load_throwsOnANonScalarValueThatIsNotASequence() {
        // given
        var yaml = """
                data: !!binary |
                  aGVsbG8=
                """;

        // then
        assertThatThrownBy(() -> FlatYamlLoader.load(yaml)).isInstanceOf(BratException.class);
    }

    @Test
    void load_throwsOnAnExplicitNullValue() {
        // given
        var yaml = """
                apiKey: null
                """;

        // then
        assertThatThrownBy(() -> FlatYamlLoader.load(yaml)).isInstanceOf(BratException.class);
    }

    @Test
    void load_throwsOnAKeyWithNoValue() {
        // given
        var yaml = """
                apiKey:
                """;

        // then
        assertThatThrownBy(() -> FlatYamlLoader.load(yaml)).isInstanceOf(BratException.class);
    }

    @Test
    void load_throwsOnABlankKey() {
        // given
        var yaml = """
                "": s3cret
                """;

        // then
        assertThatThrownBy(() -> FlatYamlLoader.load(yaml)).isInstanceOf(BratException.class);
    }

    @Test
    void load_throwsOnADuplicateKeyInTheSameMapping() {
        // given
        var yaml = """
                apiKey: first
                apiKey: second
                """;

        // then
        assertThatThrownBy(() -> FlatYamlLoader.load(yaml)).isInstanceOf(BratException.class);
    }

    @Test
    void load_throwsOnADuplicateKeyWhoseValuesAreMappings() {
        // given
        var yaml = """
                db:
                  password: one
                db:
                  username: two
                """;

        // then
        assertThatThrownBy(() -> FlatYamlLoader.load(yaml)).isInstanceOf(BratException.class);
    }

    @Test
    void load_throwsWhenAFlattenedPathCollidesWithALiteralDottedKey() {
        // given
        var yaml = """
                db:
                  password: fromNesting
                "db.password": fromDottedKey
                """;

        // then
        assertThatThrownBy(() -> FlatYamlLoader.load(yaml)).isInstanceOf(BratException.class);
    }
}
