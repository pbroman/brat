package dev.pbroman.brat.core.secrets;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        var result = underTest.create(
                Map.of(FileSecretsProviderFactory.LOCATION_PARAM, LOCATION, "address", "https://vault.example.com"));

        // then
        assertThat(result.getSecret("apiKey")).contains("s3cret");
    }

    @Test
    void create_throwsIfParamsIsNull() {
        // when / then
        assertThatThrownBy(() -> underTest.create(null)).isInstanceOf(BratException.class);
    }

    @Test
    void create_throwsIfTheLocationParamIsMissing() {
        // when / then
        assertThatThrownBy(() -> underTest.create(Map.of())).isInstanceOf(BratException.class);
    }

    @Test
    void create_throwsIfTheLocationParamIsBlank() {
        // given
        var params = new HashMap<String, String>();
        params.put(FileSecretsProviderFactory.LOCATION_PARAM, "  ");

        // when / then
        assertThatThrownBy(() -> underTest.create(params)).isInstanceOf(BratException.class);
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

    @Test
    void create_rejectsATokenInAValueNamingTheLocationAndKeyButNotTheValue(@TempDir Path dir) throws IOException {
        // given - a secrets file is data; this value could only ever be served as the literal text
        var location = secretsFile(dir, "apiKey: \"tok-${env.suffix}\"\n");

        // then
        assertThatThrownBy(() -> underTest.create(Map.of(FileSecretsProviderFactory.LOCATION_PARAM, location)))
                .isInstanceOf(BratException.class)
                .hasMessageContaining(location)
                .hasMessageContaining("secrets.apiKey")
                .hasMessageNotContaining("tok-")
                .hasMessageNotContaining("env.suffix");
    }

    @Test
    void create_rejectsATokenInANestedValueNamingTheDottedKey(@TempDir Path dir) throws IOException {
        // given
        var location = secretsFile(dir, "db:\n  password: \"${vars.pw}\"\n");

        // then
        assertThatThrownBy(() -> underTest.create(Map.of(FileSecretsProviderFactory.LOCATION_PARAM, location)))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("secrets.db.password")
                .hasMessageNotContaining("vars.pw");
    }

    @Test
    void create_neitherServesNorChecksTheTypeEntry(@TempDir Path dir) throws IOException {
        // given - the type routed the file here; whatever routed it is what checks it
        var location = secretsFile(dir, "type: \"${env.kind}\"\napiKey: s3cret\n");

        // when
        var result = underTest.create(Map.of(FileSecretsProviderFactory.LOCATION_PARAM, location));

        // then
        assertThat(result.getSecret("apiKey")).contains("s3cret");
        assertThat(result.getSecret("type")).isEmpty();
    }

    @Test
    void recognises_claimsNothing() {
        // then - plaintext is what a file nobody claims defaults to, so this factory never claims one
        assertThat(underTest.recognises("type: file\napiKey: s3cret\n")).isFalse();
    }

    @Test
    void create_servesADollarOrABraceThatIsNotAToken(@TempDir Path dir) throws IOException {
        // given - only the two characters together open a token, and passwords hold both often
        var location = secretsFile(dir, "password: \"p$ss{w}rd\"\n");

        // when
        var result = underTest.create(Map.of(FileSecretsProviderFactory.LOCATION_PARAM, location));

        // then
        assertThat(result.getSecret("password")).contains("p$ss{w}rd");
    }

    /**
     * Writes a secrets file and returns its location as the factory reads one.
     *
     * @param dir the directory to write into
     * @param content the file's content
     * @return the {@code file:} location of the written file
     * @throws IOException if the file cannot be written
     */
    private static String secretsFile(Path dir, String content) throws IOException {
        var file = dir.resolve("secrets.yaml");
        Files.writeString(file, content);
        return "file:" + file;
    }
}
