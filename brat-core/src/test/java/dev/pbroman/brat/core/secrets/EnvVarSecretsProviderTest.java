package dev.pbroman.brat.core.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

class EnvVarSecretsProviderTest {

    private final Map<String, String> environment = Map.of(
            "BRAT_SECRET_API_KEY", "fromApiKey",
            "BRAT_SECRET_API_URL", "fromApiUrl",
            "BRAT_SECRET_OAUTH2_TOKEN", "fromOauth2Token",
            "BRAT_SECRET_DB_PASSWORD", "fromDbPassword",
            "CUSTOM_API_KEY", "fromCustomPrefix"
    );

    private final EnvVarSecretsProvider underTest =
            new EnvVarSecretsProvider(EnvVarSecretsProvider.DEFAULT_PREFIX, environment::get);

    @Test
    void getSecret_readsThePrefixedUpperSnakeCaseVariable() {
        // when
        var result = underTest.getSecret("apiKey");

        // then
        assertThat(result).contains("fromApiKey");
    }

    @Test
    void getSecret_keepsAcronymsIntact() {
        // when
        var result = underTest.getSecret("apiURL");

        // then
        assertThat(result).contains("fromApiUrl");
    }

    @Test
    void getSecret_separatesAfterADigit() {
        // when
        var result = underTest.getSecret("oauth2Token");

        // then
        assertThat(result).contains("fromOauth2Token");
    }

    @Test
    void getSecret_treatsDotsAsSeparators() {
        // when
        var result = underTest.getSecret("db.password");

        // then
        assertThat(result).contains("fromDbPassword");
    }

    @Test
    void getSecret_resolvesACamelCaseKeyAndItsFlattenedFormAlike() {
        // when
        var flattened = underTest.getSecret("db.password");
        var camelCase = underTest.getSecret("dbPassword");

        // then
        assertThat(flattened).isEqualTo(camelCase);
    }

    @Test
    void getSecret_acceptsAKeyAlreadyInUpperSnakeCase() {
        // when
        var result = underTest.getSecret("API_KEY");

        // then
        assertThat(result).contains("fromApiKey");
    }

    @Test
    void getSecret_returnsEmptyIfTheVariableIsUnset() {
        // when
        var result = underTest.getSecret("missing");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void getSecret_treatsAnEmptyVariableAsUnset() {
        // given
        var withEmptyValue = new EnvVarSecretsProvider(
                EnvVarSecretsProvider.DEFAULT_PREFIX, Map.of("BRAT_SECRET_API_KEY", "")::get);

        // when
        var result = withEmptyValue.getSecret("apiKey");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void getSecret_appliesACustomPrefix() {
        // given
        var customPrefixed = new EnvVarSecretsProvider("CUSTOM_", environment::get);

        // when
        var result = customPrefixed.getSecret("apiKey");

        // then
        assertThat(result).contains("fromCustomPrefix");
    }

    @Test
    void getSecret_throwsOnNullKey() {
        // then
        assertThatThrownBy(() -> underTest.getSecret(null))
                .isInstanceOf(BratException.class);
    }

    @Test
    void getSecret_throwsOnBlankKey() {
        // then
        assertThatThrownBy(() -> underTest.getSecret("  "))
                .isInstanceOf(BratException.class);
    }
}
