package dev.pbroman.brat.core.loader;

import java.math.BigDecimal;
import java.util.Map;

import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YamlComposerTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    @Test
    void compose_convertsAMappingDocument() {
        // when
        var document = YamlComposer.compose("name: s\nphase: setup\n");

        // then
        assertThat(document.root()).isInstanceOf(Map.class);
        assertThat(asMap(document.root())).containsEntry("name", "s");
    }

    @Test
    void compose_indexesPositionsByJsonPointer() {
        // given - the pointer spelling Jackson uses for its own error paths
        var yaml = """
                name: s
                requests:
                  - name: create
                """;

        // when
        var document = YamlComposer.compose(yaml);

        // then
        assertThat(document.positions().at("/requests/0/name"))
                .hasValueSatisfying(position -> assertThat(position.line()).isEqualTo(3));
    }

    @Test
    void compose_returnsAnEmptyPositionForAPointerItDoesNotHold() {
        // when
        var document = YamlComposer.compose("name: s\n");

        // then - a caller must be able to report an error it cannot place
        assertThat(document.positions().at("/nothing/here")).isEmpty();
        assertThat(document.positions().at(null)).isEmpty();
    }

    @Test
    void compose_resolvesMergeKeysBeforeTheDuplicateCheck() {
        // given - compose flattens `<<:` with the explicit key winning, so this is one `method`, not two
        var yaml = """
                defaults: &defaults
                  method: GET
                request:
                  <<: *defaults
                  method: POST
                """;

        // when
        var document = YamlComposer.compose(yaml);

        // then
        assertThat(asMap(asMap(document.root()).get("request"))).containsEntry("method", "POST");
    }

    @Test
    void compose_typesAScalarByItsTag() {
        // given - the resolver's view, which is the one Jackson's own YAML parser would have had
        var yaml = """
                text: hello
                number: 42
                fraction: 1.5
                flag: true
                nothing:
                """;

        // when
        var root = asMap(YamlComposer.compose(yaml).root());

        // then - BigDecimal rather than Long/Double: every value reaches BRAT as text anyway
        assertThat(root.get("text")).isEqualTo("hello");
        assertThat(root.get("number")).isEqualTo(new BigDecimal("42"));
        assertThat(root.get("fraction")).isEqualTo(new BigDecimal("1.5"));
        assertThat(root.get("flag")).isEqualTo(Boolean.TRUE);
        assertThat(root).containsEntry("nothing", null);
    }

    @Test
    void compose_leavesTheYamlOneOnlyBooleansAsStrings() {
        // given - CoreSchema is YAML 1.2, whose BOOL is true/false only; `no` is a country code
        var root = asMap(YamlComposer.compose("country: no\nswitch: off\n").root());

        // then
        assertThat(root.get("country")).isEqualTo("no");
        assertThat(root.get("switch")).isEqualTo("off");
    }

    @Test
    void compose_rejectsADuplicateKeyInOneMapping() {
        // given - compose does not check this; the construct step we skip is what would have
        assertThatThrownBy(() -> YamlComposer.compose("a: 1\na: 2\n"))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("a");
    }

    @Test
    void compose_rejectsAnEmptyDocument() {
        assertThatThrownBy(() -> YamlComposer.compose("")).isInstanceOf(BratException.class);
    }

    @Test
    void compose_rejectsADocumentOfNothingButComments() {
        // given - text, so not blank, and no node at all once composed
        assertThatThrownBy(() -> YamlComposer.compose("# a suite used to live here\n"))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void compose_rejectsMalformedYaml() {
        assertThatThrownBy(() -> YamlComposer.compose("a: [unclosed\n")).isInstanceOf(BratException.class);
    }

    @Test
    void compose_reportsAnEngineLimitInItsOwnWords() {
        // given - a generated suite hitting 3 MB is a plausible accident, not a corner case
        var huge = "name: " + "x".repeat(4 * 1024 * 1024) + "\n";

        // then - BRAT's own words, not a digit that also occurs in the engine's byte count
        assertThatThrownBy(() -> YamlComposer.compose(huge))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("exceeds the 3 MB limit");
    }

    @Test
    void compose_reportsTheAliasLimitInItsOwnWords() {
        // given - 51 aliases to a mapping; the limit counts aliases to collections, not to scalars
        var yaml = "anchored: &a\n  k: v\naliases:\n" + "  - *a\n".repeat(51);

        // then
        assertThatThrownBy(() -> YamlComposer.compose(yaml))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("50");
    }
}
