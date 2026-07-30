package dev.pbroman.brat.core.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;

import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

class MapSecretsProviderTest {

    @Test
    void getSecret_returnsTheMappedValue() {
        // given
        var underTest = new MapSecretsProvider(Map.of("apiKey", "s3cret"));

        // when
        var result = underTest.getSecret("apiKey");

        // then
        assertThat(result).contains("s3cret");
    }

    @Test
    void getSecret_returnsEmptyIfTheKeyIsAbsent() {
        // given
        var underTest = new MapSecretsProvider(Map.of("apiKey", "s3cret"));

        // when
        var result = underTest.getSecret("dbToken");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void getSecret_returnsAnEmptyStringValueRatherThanTreatingItAsAbsent() {
        // given
        var underTest = new MapSecretsProvider(Map.of("apiKey", ""));

        // when
        var result = underTest.getSecret("apiKey");

        // then
        assertThat(result).contains("");
    }

    @Test
    void getSecret_matchesTheKeyExactly() {
        // given
        var underTest = new MapSecretsProvider(Map.of("db.password", "s3cret"));

        // when
        var result = underTest.getSecret("dbPassword");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void getSecret_returnsEmptyForAnEmptyMap() {
        // given
        var underTest = new MapSecretsProvider(Map.of());

        // when
        var result = underTest.getSecret("apiKey");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void getSecret_throwsOnNullKey() {
        // given
        var underTest = new MapSecretsProvider(Map.of("apiKey", "s3cret"));

        // then
        assertThatThrownBy(() -> underTest.getSecret(null))
                .isInstanceOf(BratException.class);
    }

    @Test
    void getSecret_throwsOnBlankKey() {
        // given
        var underTest = new MapSecretsProvider(Map.of("apiKey", "s3cret"));

        // then
        assertThatThrownBy(() -> underTest.getSecret("  "))
                .isInstanceOf(BratException.class);
    }

    @Test
    void constructor_throwsOnNullMap() {
        // then
        assertThatThrownBy(() -> new MapSecretsProvider(null))
                .isInstanceOf(BratException.class);
    }

    @Test
    void constructor_throwsOnNullKey() {
        // given
        var secrets = new HashMap<String, String>();
        secrets.put(null, "s3cret");

        // then
        assertThatThrownBy(() -> new MapSecretsProvider(secrets))
                .isInstanceOf(BratException.class);
    }

    @Test
    void constructor_throwsOnBlankKey() {
        // given
        var secrets = new HashMap<String, String>();
        secrets.put("  ", "s3cret");

        // then
        assertThatThrownBy(() -> new MapSecretsProvider(secrets))
                .isInstanceOf(BratException.class);
    }

    @Test
    void constructor_throwsOnNullValue() {
        // given
        var secrets = new HashMap<String, String>();
        secrets.put("apiKey", null);

        // then
        assertThatThrownBy(() -> new MapSecretsProvider(secrets))
                .isInstanceOf(BratException.class);
    }

    @Test
    void constructor_copiesTheMap() {
        // given
        var secrets = new HashMap<String, String>();
        secrets.put("apiKey", "s3cret");
        var underTest = new MapSecretsProvider(secrets);

        // when
        secrets.clear();

        // then
        assertThat(underTest.getSecret("apiKey")).contains("s3cret");
    }
}
