package dev.pbroman.brat.core.schema;

import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SuiteSchemaJsonTest {

    @Test
    void json_isADraft202012Schema() {
        // when
        var json = SuiteSchema.json();

        // then
        var schema = JsonMapper.builder().build().readTree(json);
        assertThat(schema.path("$schema").asString()).isEqualTo("https://json-schema.org/draft/2020-12/schema");
    }

    @Test
    void json_endsInANewline() {
        // when
        var json = SuiteSchema.json();

        // then
        assertThat(json).endsWith("}\n");
    }

    @Test
    void json_throwsIfTheResourceIsMissing() {
        // when / then
        assertThatThrownBy(() -> SuiteSchema.json("dev/pbroman/brat/core/schema/no-such.schema.json"))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("no-such.schema.json");
    }
}
