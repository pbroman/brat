package dev.pbroman.brat.core.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;

import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

class FileSecretsProviderFactoryTest {

    private static final String LOCATION = "classpath:secrets/secrets001.yaml";

    private final FileSecretsProviderFactory underTest = new FileSecretsProviderFactory();

    @Test
    void type_isFile() {
        // when
        var result = underTest.type();

        // then
        assertThat(result).isEqualTo("file");
    }

    @Test
    void create_servesTheFilesEntries() {
        // when
        var result = underTest.create(Map.of(FileSecretsProviderFactory.LOCATION_PARAM, LOCATION));

        // then
        assertThat(result.getSecret("apiKey")).contains("s3cret");
    }

    @Test
    void create_flattensNestedEntriesToDottedKeys() {
        // when
        var result = underTest.create(Map.of(FileSecretsProviderFactory.LOCATION_PARAM, LOCATION));

        // then
        assertThat(result.getSecret("db.password")).contains("p4ss");
    }

    @Test
    void create_doesNotServeTheFilesTypeDeclarationAsASecret() {
        // when
        var result = underTest.create(Map.of(FileSecretsProviderFactory.LOCATION_PARAM, LOCATION));

        // then
        assertThat(result.getSecret("type")).isEmpty();
    }

    @Test
    void create_resolvesNothingForAFileWithoutEntries() {
        // when
        var result = underTest.create(
                Map.of(FileSecretsProviderFactory.LOCATION_PARAM, "classpath:secrets/secrets-empty.yaml"));

        // then
        assertThat(result.getSecret("apiKey")).isEmpty();
    }

    @Test
    void create_ignoresParamsItDoesNotUse() {
        // when
        var result = underTest.create(Map.of(
                FileSecretsProviderFactory.LOCATION_PARAM, LOCATION,
                "address", "https://vault.example.com"));

        // then
        assertThat(result.getSecret("apiKey")).contains("s3cret");
    }

    @Test
    void create_throwsIfParamsIsNull() {
        // when / then
        assertThatThrownBy(() -> underTest.create(null))
                .isInstanceOf(BratException.class);
    }

    @Test
    void create_throwsIfTheLocationParamIsMissing() {
        // when / then
        assertThatThrownBy(() -> underTest.create(Map.of()))
                .isInstanceOf(BratException.class);
    }

    @Test
    void create_throwsIfTheLocationParamIsBlank() {
        // given
        var params = new HashMap<String, String>();
        params.put(FileSecretsProviderFactory.LOCATION_PARAM, "  ");

        // when / then
        assertThatThrownBy(() -> underTest.create(params))
                .isInstanceOf(BratException.class);
    }

    @Test
    void create_throwsIfTheFileCannotBeRead() {
        // when / then
        assertThatThrownBy(() -> underTest.create(
                Map.of(FileSecretsProviderFactory.LOCATION_PARAM, "classpath:secrets/does-not-exist.yaml")))
                .isInstanceOf(BratException.class);
    }

    @Test
    void create_throwsIfTheFileIsNotAFlatYamlDocument() {
        // when / then
        assertThatThrownBy(() -> underTest.create(
                Map.of(FileSecretsProviderFactory.LOCATION_PARAM, "classpath:resource-reader/greeting.txt")))
                .isInstanceOf(BratException.class);
    }
}
