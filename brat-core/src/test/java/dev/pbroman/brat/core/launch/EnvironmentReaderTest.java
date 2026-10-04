package dev.pbroman.brat.core.launch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.secrets.SecretsSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class EnvironmentReaderTest {

    @TempDir
    Path dir;

    // ---------- the directory itself ----------

    @Test
    void read_returnsAnEmptyEnvironmentForAnEmptyDirectory() {
        // when
        var result = EnvironmentReader.read(dir.toString(), Map.of());

        // then
        assertThat(result.env()).isEmpty();
        assertThat(result.params()).isEmpty();
        assertThat(result.secretsConfig().providerParams()).isEmpty();
        assertThat(result.secretsConfig().sources()).isEmpty();
        assertThat(result.suiteLocation()).isNull();
    }

    @Test
    void read_acceptsAFilePrefixedDirectory() throws IOException {
        // given
        write("env.yaml", "baseUrl: http://localhost:8080\n");

        // when
        var result = EnvironmentReader.read("file:" + dir, Map.of());

        // then
        assertThat(result.env()).containsEntry("baseUrl", "http://localhost:8080");
    }

    @Test
    void read_carriesTheParamsItWasGiven() {
        // when
        var result = EnvironmentReader.read(dir.toString(), Map.of("timeout", "20000", "env.baseUrl", "http://x"));

        // then
        assertThat(result.params()).containsOnly(entry("timeout", "20000"), entry("env.baseUrl", "http://x"));
    }

    @Test
    void read_doesNotModifyTheParams() {
        // given
        var params = new HashMap<>(Map.of("timeout", "20000"));

        // when
        EnvironmentReader.read(dir.toString(), params);

        // then
        assertThat(params).containsOnly(entry("timeout", "20000"));
    }

    @Test
    void read_rejectsAClasspathDirectory() {
        // then
        assertThatThrownBy(() -> EnvironmentReader.read("classpath:envs/dev", Map.of()))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("classpath:envs/dev");
    }

    @Test
    void read_rejectsAMissingDirectoryNamingIt() {
        // given
        var missing = dir.resolve("nope").toString();

        // then
        assertThatThrownBy(() -> EnvironmentReader.read(missing, Map.of()))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("nope");
    }

    @Test
    void read_rejectsAFileInsteadOfADirectoryNamingIt() throws IOException {
        // given
        write("env.yaml", "a: b\n");

        // then
        assertThatThrownBy(() -> EnvironmentReader.read(dir.resolve("env.yaml").toString(), Map.of()))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("env.yaml");
    }

    @Test
    void read_rejectsADirectoryThatCannotBeListedNamingIt() throws IOException {
        // given - a directory nobody may read; meaningless on a filesystem without POSIX permissions, or as root
        assumeTrue(dir.getFileSystem().supportedFileAttributeViews().contains("posix"));
        var locked = Files.createDirectory(dir.resolve("locked"));
        Files.setPosixFilePermissions(locked, Set.of());
        assumeFalse(Files.isReadable(locked), "running with permission to read any directory");

        try {
            // then
            assertThatThrownBy(() -> EnvironmentReader.read(locked.toString(), Map.of()))
                    .isInstanceOf(BratException.class)
                    .hasMessageContaining("locked");
        } finally {
            Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    void read_namesARecognisedFileThatCannotBeRead() throws IOException {
        // given - not UTF-8, so its content cannot be read as text at all
        Files.write(dir.resolve("env.yaml"), new byte[] {(byte) 0xFF, (byte) 0xFE, 'a', ':', ' ', 'b'});

        // then
        assertThatThrownBy(() -> EnvironmentReader.read(dir.toString(), Map.of()))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("env.yaml");
    }

    @Test
    void read_throwsForANullOrBlankDirectoryOrNullParams() {
        // then
        assertThatThrownBy(() -> EnvironmentReader.read(null, Map.of())).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> EnvironmentReader.read(" ", Map.of())).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> EnvironmentReader.read(dir.toString(), null)).isInstanceOf(BratException.class);
    }

    // ---------- env ----------

    @Test
    void read_readsEnvYamlFlatAndAsText() throws IOException {
        // given
        write("env.yaml", """
                baseUrl: http://localhost:8080
                tenant: 0123
                db:
                  host: db.dev.internal
                """);

        // when
        var result = EnvironmentReader.read(dir.toString(), Map.of());

        // then
        assertThat(result.env())
                .containsOnly(
                        entry("baseUrl", "http://localhost:8080"),
                        entry("tenant", "0123"),
                        entry("db.host", "db.dev.internal"));
    }

    @Test
    void read_acceptsEnvYml() throws IOException {
        // given
        write("env.yml", "baseUrl: http://localhost:8080\n");

        // when
        var result = EnvironmentReader.read(dir.toString(), Map.of());

        // then
        assertThat(result.env()).containsEntry("baseUrl", "http://localhost:8080");
    }

    @Test
    void read_rejectsEnvYamlAndEnvYmlTogetherNamingBoth() throws IOException {
        // given
        write("env.yaml", "a: b\n");
        write("env.yml", "a: c\n");

        // then
        assertThatThrownBy(() -> EnvironmentReader.read(dir.toString(), Map.of()))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("env.yaml")
                .hasMessageContaining("env.yml");
    }

    @Test
    void read_rejectsATokenInEnvYamlNamingTheKeyButNotTheValue() throws IOException {
        // given
        write("env.yaml", "ordersUrl: \"${env.baseUrl}/orders\"\n");

        // then
        assertThatThrownBy(() -> EnvironmentReader.read(dir.toString(), Map.of()))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("env.ordersUrl")
                .hasMessageNotContaining("/orders");
    }

    @Test
    void read_namesEnvYamlWhenItCannotBeReadAndDoesNotQuoteIt() throws IOException {
        // given - a duplicate key is refused by the flat reader
        write("env.yaml", "token: s3cr3t\ntoken: again\n");

        // then
        assertThatThrownBy(() -> EnvironmentReader.read(dir.toString(), Map.of()))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("env.yaml")
                .hasMessageNotContaining("s3cr3t");
    }

    @Test
    void read_rejectsATokenInAParam() {
        // then
        assertThatThrownBy(() -> EnvironmentReader.read(dir.toString(), Map.of("wait", "${env.x}")))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("params.wait");
    }

    // ---------- providers ----------

    @Test
    void read_readsProvidersYamlIntoTheProviderParameters() throws IOException {
        // given
        write("providers.yaml", """
                providers:
                  vault:
                    address: "${env.vaultUrl}"
                """);

        // when
        var result = EnvironmentReader.read(dir.toString(), Map.of());

        // then - a provider document configures behaviour, so its tokens stay for the chain builder
        assertThat(result.secretsConfig().paramsFor("vault")).containsEntry("address", "${env.vaultUrl}");
    }

    @Test
    void read_rejectsProvidersYamlAndProvidersYmlTogether() throws IOException {
        // given
        write("providers.yaml", "providers: {}\n");
        write("providers.yml", "providers: {}\n");

        // then
        assertThatThrownBy(() -> EnvironmentReader.read(dir.toString(), Map.of()))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("providers.yaml")
                .hasMessageContaining("providers.yml");
    }

    @Test
    void read_namesProvidersYamlWhenItIsNotAProviderDocument() throws IOException {
        // given
        write("providers.yaml", "connections:\n  vault:\n    address: x\n");

        // then
        assertThatThrownBy(() -> EnvironmentReader.read(dir.toString(), Map.of()))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("providers.yaml");
    }

    // ---------- secrets ----------

    @Test
    void read_turnsSecretsYamlIntoAFileSourceAtItsAbsoluteLocation() throws IOException {
        // given
        write("secrets.yaml", "apiKey: s3cret\n");

        // when
        var result = EnvironmentReader.read(dir.toString(), Map.of());

        // then
        assertThat(result.secretsConfig().sources())
                .containsExactly(new SecretsSource("file", Map.of("location", "file:" + dir.resolve("secrets.yaml"))));
    }

    @Test
    void read_takesASecretsFilesTypeFromItsTypeEntry() throws IOException {
        // given
        write("secrets001-vault.yaml", "type: vault\npath: kv/orders\n");

        // when
        var result = EnvironmentReader.read(dir.toString(), Map.of());

        // then
        assertThat(result.secretsConfig().sources())
                .singleElement()
                .satisfies(source -> assertThat(source.type()).isEqualTo("vault"));
    }

    @Test
    void read_ordersSecretsFilesByTheirNamesComparedAsStrings() throws IOException {
        // given - written in an order that is none of the interesting ones
        for (var name : List.of(
                "secrets9.yaml",
                "secrets001-file.yaml",
                "secrets10.yaml",
                "secrets.yaml",
                "secrets002.yml",
                "secrets001-Vault.yaml")) {
            write(name, "k: v\n");
        }

        // when
        var result = EnvironmentReader.read(dir.toString(), Map.of());

        // then - '.' before digits, uppercase before lowercase, and no numeric comparison
        assertThat(result.secretsConfig().sources())
                .extracting(source -> Path.of(source.params().get("location").substring("file:".length()))
                        .getFileName()
                        .toString())
                .containsExactly(
                        "secrets.yaml",
                        "secrets001-Vault.yaml",
                        "secrets001-file.yaml",
                        "secrets002.yml",
                        "secrets10.yaml",
                        "secrets9.yaml");
    }

    @Test
    void read_rejectsTwoSecretsFilesDifferingOnlyInTheirExtension() throws IOException {
        // given
        write("secrets001.yaml", "a: b\n");
        write("secrets001.yml", "a: c\n");

        // then
        assertThatThrownBy(() -> EnvironmentReader.read(dir.toString(), Map.of()))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("secrets001.yaml")
                .hasMessageContaining("secrets001.yml");
    }

    @Test
    void read_namesASecretsFileWhoseTypeIsBlank() throws IOException {
        // given
        write("secrets.yaml", "type: \"\"\napiKey: s3cret\n");

        // then
        assertThatThrownBy(() -> EnvironmentReader.read(dir.toString(), Map.of()))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("secrets.yaml")
                .hasMessageNotContaining("s3cret");
    }

    @Test
    void read_namesASecretsFileThatIsNotAFlatDocumentAndDoesNotQuoteIt() throws IOException {
        // given
        write("secrets.yaml", "apiKey: s3cret\nlist:\n  - a\n");

        // then
        assertThatThrownBy(() -> EnvironmentReader.read(dir.toString(), Map.of()))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("secrets.yaml")
                .hasMessageNotContaining("s3cret");
    }

    // ---------- what is not read ----------

    @Test
    void read_ignoresYamlFilesItDoesNotRecognise() throws IOException {
        // given - each would be a source or the env under a looser rule
        for (var name : List.of(
                "secrets.example.yaml",
                "secret001.yaml",
                "secrets-local.yaml",
                "Secrets.yaml",
                "environment.yaml",
                "Env.yaml",
                "env-common.yaml")) {
            write(name, "k: v\n");
        }

        // when
        var result = EnvironmentReader.read(dir.toString(), Map.of());

        // then
        assertThat(result.env()).isEmpty();
        assertThat(result.secretsConfig().sources()).isEmpty();
    }

    @Test
    void read_ignoresFilesThatAreNotYaml() throws IOException {
        // given
        write("README.md", "# dev\n");
        write(".gitignore", "secrets.yaml\n");
        write("env.json", "{\"a\": \"b\"}\n");

        // when
        var result = EnvironmentReader.read(dir.toString(), Map.of());

        // then
        assertThat(result.env()).isEmpty();
        assertThat(result.secretsConfig().sources()).isEmpty();
    }

    @Test
    void read_ignoresSubdirectoriesWhateverTheirName() throws IOException {
        // given - a directory named like a recognised file, and a recognised file one level down
        Files.createDirectory(dir.resolve("secrets001.yaml"));
        Files.createDirectory(dir.resolve("nested"));
        Files.writeString(dir.resolve("nested/env.yaml"), "baseUrl: http://nested\n");

        // when
        var result = EnvironmentReader.read(dir.toString(), Map.of());

        // then
        assertThat(result.env()).isEmpty();
        assertThat(result.secretsConfig().sources()).isEmpty();
    }

    /**
     * Writes a file into the environment directory under test.
     *
     * @param name the file name
     * @param content its content
     * @throws IOException if it cannot be written
     */
    private void write(String name, String content) throws IOException {
        Files.writeString(dir.resolve(name), content);
    }
}
