package dev.pbroman.brat.openapi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.dataformat.yaml.YAMLMapper;

import static org.assertj.core.api.Assertions.assertThat;

class YamlTextTest {

    private static final YAMLMapper YAML = YAMLMapper.builder().build();

    @ParameterizedTest
    @ValueSource(
            strings = {
                "OPEN",
                "http://localhost:8080",
                "health check",
                "X-Request-Id",
                "orders.read orders.write",
                "4711",
                "1e3",
                "0x1F",
                ".inf",
                "yes",
                "No",
                "ON",
                "y",
                "null",
                "~",
                "",
                " leading",
                "trailing ",
                "a: b",
                "a #b",
                "#comment",
                "- item",
                "*alias",
                "&anchor",
                "!tag",
                "|",
                ">",
                "@at",
                "`tick",
                "%percent",
                "'single'",
                "\"double\"",
                "back\\slash",
                "${env.baseUrl}/orders",
                "{flow}",
                "[flow]",
                "line\nbreak",
                "tab\there",
                "carriage\rreturn",
                "bell\u0007",
                "delete\u007f",
                "next\u0085line",
                "line\u2028separator",
                "paragraph\u2029separator",
                "ends with colon:",
                "über",
                "GET orders {orderId}"
            })
    void scalar_readsBackAsTheSameText(String value) {
        // when
        var scalar = YamlText.scalar(value);

        // then
        assertThat(scalar).doesNotContain("\n");
        var asValue = YAML.readTree("key: " + scalar + "\n").get("key");
        assertThat(asValue.isString()).as(scalar).isTrue();
        assertThat(asValue.asString()).isEqualTo(value);
        var asKey = YAML.readTree(scalar + ": x\n");
        assertThat(asKey.propertyNames()).containsExactly(value);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"OPEN", "http://localhost:8080", "health check", "X-Request-Id", "orders.read orders.write"})
    void scalar_writesSafeTextPlain(String value) {
        // when
        var scalar = YamlText.scalar(value);

        // then
        assertThat(scalar).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"4711", "yes", "a: b", "${env.x}", "", "true", "Null"})
    void scalar_quotesTextYamlWouldReadOtherwise(String value) {
        // when
        var scalar = YamlText.scalar(value);

        // then
        assertThat(scalar).startsWith("\"").endsWith("\"");
    }

    @Test
    void commentText_putsEverythingOnOneLine() {
        // when
        var text = YamlText.commentText("orders\nopenapi.yaml\r\tv2\u2028x");

        // then
        assertThat(text).isEqualTo("orders openapi.yaml  v2 x");
    }
}
