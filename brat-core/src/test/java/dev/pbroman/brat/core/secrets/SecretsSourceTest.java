package dev.pbroman.brat.core.secrets;

import java.util.HashMap;
import java.util.Map;

import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

class SecretsSourceTest {

    @Test
    void constructor_keepsTypeAndParams() {
        // when
        var underTest = new SecretsSource("file", Map.of("location", "classpath:secrets/secrets001.yaml"));

        // then
        assertThat(underTest.type()).isEqualTo("file");
        assertThat(underTest.params()).containsOnly(entry("location", "classpath:secrets/secrets001.yaml"));
    }

    @Test
    void constructor_acceptsEmptyParams() {
        // when
        var underTest = new SecretsSource("sysenv", Map.of());

        // then
        assertThat(underTest.params()).isEmpty();
    }

    @Test
    void constructor_copiesTheParams() {
        // given
        var params = new HashMap<String, String>();
        params.put("location", "one.yaml");

        // when
        var underTest = new SecretsSource("file", params);
        params.put("location", "two.yaml");

        // then
        assertThat(underTest.params()).containsOnly(entry("location", "one.yaml"));
    }

    @Test
    void constructor_throwsIfTypeIsNull() {
        // when / then
        assertThatThrownBy(() -> new SecretsSource(null, Map.of())).isInstanceOf(BratException.class);
    }

    @Test
    void constructor_throwsIfTypeIsBlank() {
        // when / then
        assertThatThrownBy(() -> new SecretsSource("  ", Map.of())).isInstanceOf(BratException.class);
    }

    @Test
    void constructor_throwsIfParamsIsNull() {
        // when / then
        assertThatThrownBy(() -> new SecretsSource("file", null)).isInstanceOf(BratException.class);
    }

    @Test
    void constructor_throwsIfAParamKeyIsBlank() {
        // given
        var params = new HashMap<String, String>();
        params.put(" ", "value");

        // when / then
        assertThatThrownBy(() -> new SecretsSource("file", params)).isInstanceOf(BratException.class);
    }

    @Test
    void constructor_throwsIfAParamValueIsNull() {
        // given
        var params = new HashMap<String, String>();
        params.put("location", null);

        // when / then
        assertThatThrownBy(() -> new SecretsSource("file", params)).isInstanceOf(BratException.class);
    }
}
