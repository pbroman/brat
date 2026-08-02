package dev.pbroman.brat.core.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

class SecretsProviderConfigLoaderTest {

    @Test
    void load_readsEachProviderTypesParams() {
        // given
        var yaml = """
                providers:
                  vault:
                    address: https://vault.example.com
                  sysenv:
                    prefix: BRAT_SECRET_
                """;

        // when
        var result = SecretsProviderConfigLoader.load(yaml);

        // then
        assertThat(result.providerParams()).containsOnlyKeys("vault", "sysenv");
        assertThat(result.paramsFor("vault")).containsOnly(entry("address", "https://vault.example.com"));
        assertThat(result.paramsFor("sysenv")).containsOnly(entry("prefix", "BRAT_SECRET_"));
    }

    @Test
    void load_flattensNestedParamsToDottedKeys() {
        // given
        var yaml = """
                providers:
                  vault:
                    auth:
                      method: approle
                      roleId: r0le
                """;

        // when
        var result = SecretsProviderConfigLoader.load(yaml);

        // then
        assertThat(result.paramsFor("vault"))
                .containsOnly(entry("auth.method", "approle"), entry("auth.roleId", "r0le"));
    }

    @Test
    void load_readsADottedParamKeyLikeANestedOne() {
        // given
        var yaml = """
                providers:
                  vault:
                    auth.method: approle
                """;

        // when
        var result = SecretsProviderConfigLoader.load(yaml);

        // then
        assertThat(result.paramsFor("vault")).containsOnly(entry("auth.method", "approle"));
    }

    @Test
    void load_leavesInterpolationTokensUnresolved() {
        // given
        var yaml = """
                providers:
                  vault:
                    token: ${secrets.vaultToken}
                """;

        // when
        var result = SecretsProviderConfigLoader.load(yaml);

        // then
        assertThat(result.paramsFor("vault")).containsOnly(entry("token", "${secrets.vaultToken}"));
    }

    @Test
    void load_returnsNoSources() {
        // given
        var yaml = """
                providers:
                  vault:
                    address: https://vault.example.com
                """;

        // when
        var result = SecretsProviderConfigLoader.load(yaml);

        // then
        assertThat(result.sources()).isEmpty();
    }

    @Test
    void load_returnsNoParamsForAnEmptyDocument() {
        // when
        var result = SecretsProviderConfigLoader.load("");

        // then
        assertThat(result.providerParams()).isEmpty();
    }

    @Test
    void load_returnsNoParamsForACommentsOnlyDocument() {
        // when
        var result = SecretsProviderConfigLoader.load("# nothing configured here\n");

        // then
        assertThat(result.providerParams()).isEmpty();
    }

    @Test
    void load_returnsNoParamsForAnEmptyProvidersMapping() {
        // when
        var result = SecretsProviderConfigLoader.load("providers: {}\n");

        // then
        assertThat(result.providerParams()).isEmpty();
    }

    @Test
    void load_throwsIfTheYamlIsNull() {
        // when / then
        assertThatThrownBy(() -> SecretsProviderConfigLoader.load(null))
                .isInstanceOf(BratException.class);
    }

    @Test
    void load_throwsForATopLevelKeyOtherThanProviders() {
        // given
        var yaml = """
                providers:
                  vault:
                    address: https://vault.example.com
                sources: secrets001.yaml
                """;

        // when / then
        assertThatThrownBy(() -> SecretsProviderConfigLoader.load(yaml))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("sources");
    }

    @Test
    void load_throwsIfTheProvidersSectionIsAScalar() {
        // when / then
        assertThatThrownBy(() -> SecretsProviderConfigLoader.load("providers: vault\n"))
                .isInstanceOf(BratException.class);
    }

    @Test
    void load_throwsIfAProviderTypeHasNoParams() {
        // given
        var yaml = """
                providers:
                  vault: https://vault.example.com
                """;

        // when / then
        assertThatThrownBy(() -> SecretsProviderConfigLoader.load(yaml))
                .isInstanceOf(BratException.class);
    }

    @Test
    void load_throwsIfTheProvidersKeyIsEmpty() {
        // when / then
        assertThatThrownBy(() -> SecretsProviderConfigLoader.load("providers:\n"))
                .isInstanceOf(BratException.class);
    }

    @Test
    void load_throwsForAMalformedDocument() {
        // when / then
        assertThatThrownBy(() -> SecretsProviderConfigLoader.load("providers: [oops\n"))
                .isInstanceOf(BratException.class);
    }

    @Test
    void load_throwsIfTheTopLevelIsNotAMapping() {
        // when / then
        assertThatThrownBy(() -> SecretsProviderConfigLoader.load("- providers\n"))
                .isInstanceOf(BratException.class);
    }

    @Test
    void load_doesNotQuoteTheDocumentInAParseError() {
        // given
        var yaml = """
                providers:
                  vault:
                    token: "unterminated
                """;

        // when / then
        assertThatThrownBy(() -> SecretsProviderConfigLoader.load(yaml))
                .isInstanceOf(BratException.class)
                .hasMessageNotContaining("unterminated")
                .satisfies(thrown -> assertThat(thrown.getCause()).isNull());
    }
}
