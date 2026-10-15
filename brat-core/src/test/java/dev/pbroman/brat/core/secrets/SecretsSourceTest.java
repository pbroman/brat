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
    void constructor_keepsTypeAndArgs() {
        // when
        var underTest = new SecretsSource("file", Map.of("location", "classpath:secrets/secrets001.yaml"));

        // then
        assertThat(underTest.type()).isEqualTo("file");
        assertThat(underTest.args()).containsOnly(entry("location", "classpath:secrets/secrets001.yaml"));
    }

    @Test
    void constructor_acceptsEmptyArgs() {
        // when
        var underTest = new SecretsSource("sysenv", Map.of());

        // then
        assertThat(underTest.args()).isEmpty();
    }

    @Test
    void constructor_copiesTheArgs() {
        // given
        var args = new HashMap<String, String>();
        args.put("location", "one.yaml");

        // when
        var underTest = new SecretsSource("file", args);
        args.put("location", "two.yaml");

        // then
        assertThat(underTest.args()).containsOnly(entry("location", "one.yaml"));
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
    void constructor_throwsIfArgsIsNull() {
        // when / then
        assertThatThrownBy(() -> new SecretsSource("file", null)).isInstanceOf(BratException.class);
    }

    @Test
    void constructor_throwsIfAParamKeyIsBlank() {
        // given
        var args = new HashMap<String, String>();
        args.put(" ", "value");

        // when / then
        assertThatThrownBy(() -> new SecretsSource("file", args)).isInstanceOf(BratException.class);
    }

    @Test
    void constructor_throwsIfAParamValueIsNull() {
        // given
        var args = new HashMap<String, String>();
        args.put("location", null);

        // when / then
        assertThatThrownBy(() -> new SecretsSource("file", args)).isInstanceOf(BratException.class);
    }
}
