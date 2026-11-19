package dev.pbroman.brat.core.secrets;

import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretsUtilsTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "clientSecret, BRAT_SECRET_CLIENT_SECRET",
        "apiURL, BRAT_SECRET_API_URL",
        "key2Value, BRAT_SECRET_KEY2_VALUE",
        "db.password, BRAT_SECRET_DB_PASSWORD",
        "db-password, BRAT_SECRET_DB_PASSWORD",
        "dbPassword, BRAT_SECRET_DB_PASSWORD",
        "API_KEY, BRAT_SECRET_API_KEY"
    })
    void envVarName_isThePrefixAndTheKeyInUpperSnakeCase(String key, String expected) {
        // when
        var name = SecretsUtils.envVarName(SecretsUtils.DEFAULT_ENV_PREFIX, key);

        // then
        assertThat(name).isEqualTo(expected);
    }

    @Test
    void envVarName_treatsANullPrefixAsNone() {
        // when
        var name = SecretsUtils.envVarName(null, "clientSecret");

        // then
        assertThat(name).isEqualTo("CLIENT_SECRET");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "  ")
    void envVarName_throwsForANullOrBlankKey(String key) {
        // when / then
        assertThatThrownBy(() -> SecretsUtils.envVarName(SecretsUtils.DEFAULT_ENV_PREFIX, key))
                .isInstanceOf(BratException.class);
    }
}
