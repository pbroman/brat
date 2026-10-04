package dev.pbroman.brat.core.launch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.pbroman.brat.core.api.secrets.SecretsProvider;
import dev.pbroman.brat.core.api.secrets.SecretsProviderFactory;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.secrets.FileSecretsProviderFactory;
import dev.pbroman.brat.core.secrets.SecretsSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class EnvironmentReaderTest {

    /** Claims a file carrying a top-level {@code sops:} block, as an encrypting format's factory would. */
    private static final SecretsProviderFactory SOPS = recognising("sops", "sops:");

    @TempDir
    Path dir;

    private final EnvironmentReader reader = new EnvironmentReader(List.of(new FileSecretsProviderFactory(), SOPS));

    // ---------- the directory itself ----------

    @Test
    void read_returnsAnEmptyEnvironmentForAnEmptyDirectory() {
        // when
        var result = reader.read(dir.toString(), Map.of());

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
        var result = reader.read("file:" + dir, Map.of());

        // then
        assertThat(result.env()).containsEntry("baseUrl", "http://localhost:8080");
    }

    @Test
    void read_carriesTheParamsItWasGiven() {
        // when
        var result = reader.read(dir.toString(), Map.of("timeout", "20000", "env.baseUrl", "http://x"));

        // then
        assertThat(result.params()).containsOnly(entry("timeout", "20000"), entry("env.baseUrl", "http://x"));
    }

    @Test
    void read_doesNotModifyTheParams() {
        // given
        var params = new HashMap<>(Map.of("timeout", "20000"));

        // when
        reader.read(dir.toString(), params);

        // then
        assertThat(params).containsOnly(entry("timeout", "20000"));
    }

    @Test
    void read_rejectsAClasspathDirectory() {
        // then
        assertThatThrownBy(() -> reader.read("classpath:envs/dev", Map.of()))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("classpath:envs/dev");
    }

    @Test
    void read_rejectsAMissingDirectoryNamingIt() {
        // given
        var missing = dir.resolve("nope").toString();

        // then
        assertThatThrownBy(() -> reader.read(missing, Map.of()))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("nope");
    }

    @Test
    void read_rejectsAFileInsteadOfADirectoryNamingIt() throws IOException {
        // given
        write("env.yaml", "a: b\n");

        // then
        assertThatThrownBy(() -> reader.read(dir.resolve("env.yaml").toString(), Map.of()))
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
            assertThatThrownBy(() -> reader.read(locked.toString(), Map.of()))
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
        assertThatThrownBy(() -> reader.read(dir.toString(), Map.of()))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("env.yaml");
    }

    @Test
    void read_throwsForANullOrBlankDirectoryOrNullParams() {
        // then
        assertThatThrownBy(() -> reader.read(null, Map.of())).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> reader.read(" ", Map.of())).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> reader.read(dir.toString(), null)).isInstanceOf(BratException.class);
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
        var result = reader.read(dir.toString(), Map.of());

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
        var result = reader.read(dir.toString(), Map.of());

        // then
        assertThat(result.env()).containsEntry("baseUrl", "http://localhost:8080");
    }

    @Test
    void read_rejectsEnvYamlAndEnvYmlTogetherNamingBoth() throws IOException {
        // given
        write("env.yaml", "a: b\n");
        write("env.yml", "a: c\n");

        // then
        assertThatThrownBy(() -> reader.read(dir.toString(), Map.of()))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("env.yaml")
                .hasMessageContaining("env.yml");
    }

    @Test
    void read_rejectsATokenInEnvYamlNamingTheKeyButNotTheValue() throws IOException {
        // given
        write("env.yaml", "ordersUrl: \"${env.baseUrl}/orders\"\n");

        // then
        assertThatThrownBy(() -> reader.read(dir.toString(), Map.of()))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("env.ordersUrl")
                .hasMessageNotContaining("/orders");
    }

    @Test
    void read_namesEnvYamlWhenItCannotBeReadAndDoesNotQuoteIt() throws IOException {
        // given - a duplicate key is refused by the flat reader
        write("env.yaml", "token: s3cr3t\ntoken: again\n");

        // then
        assertThatThrownBy(() -> reader.read(dir.toString(), Map.of()))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("env.yaml")
                .hasMessageNotContaining("s3cr3t");
    }

    @Test
    void read_rejectsATokenInAParam() {
        // then
        assertThatThrownBy(() -> reader.read(dir.toString(), Map.of("wait", "${env.x}")))
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
        var result = reader.read(dir.toString(), Map.of());

        // then - a provider document configures behaviour, so its tokens stay for the chain builder
        assertThat(result.secretsConfig().paramsFor("vault")).containsEntry("address", "${env.vaultUrl}");
    }

    @Test
    void read_rejectsProvidersYamlAndProvidersYmlTogether() throws IOException {
        // given
        write("providers.yaml", "providers: {}\n");
        write("providers.yml", "providers: {}\n");

        // then
        assertThatThrownBy(() -> reader.read(dir.toString(), Map.of()))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("providers.yaml")
                .hasMessageContaining("providers.yml");
    }

    @Test
    void read_namesProvidersYamlWhenItIsNotAProviderDocument() throws IOException {
        // given
        write("providers.yaml", "connections:\n  vault:\n    address: x\n");

        // then
        assertThatThrownBy(() -> reader.read(dir.toString(), Map.of()))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("providers.yaml");
    }

    // ---------- secrets ----------

    @Test
    void read_turnsSecretsYamlIntoAFileSourceAtItsAbsoluteLocation() throws IOException {
        // given
        write("secrets.yaml", "apiKey: s3cret\n");

        // when
        var result = reader.read(dir.toString(), Map.of());

        // then
        assertThat(result.secretsConfig().sources())
                .containsExactly(new SecretsSource("file", Map.of("location", "file:" + dir.resolve("secrets.yaml"))));
    }

    @Test
    void read_takesASecretsFilesTypeFromItsTypeEntry() throws IOException {
        // given
        write("secrets001-vault.yaml", "type: vault\npath: kv/orders\n");

        // when
        var result = reader.read(dir.toString(), Map.of());

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
        var result = reader.read(dir.toString(), Map.of());

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
        assertThatThrownBy(() -> reader.read(dir.toString(), Map.of()))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("secrets001.yaml")
                .hasMessageContaining("secrets001.yml");
    }

    @Test
    void read_namesASecretsFileWhoseTypeIsBlank() throws IOException {
        // given
        write("secrets.yaml", "type: \"\"\napiKey: s3cret\n");

        // then
        assertThatThrownBy(() -> reader.read(dir.toString(), Map.of()))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("secrets.yaml")
                .hasMessageNotContaining("s3cret");
    }

    @Test
    void read_readsTheTypeOfASecretsFileThatIsNotFlat() throws IOException {
        // given - only the top level is read for the type, so a locator file may hold any YAML
        write("secrets001.yaml", "type: vault\npaths:\n  - kv/orders\n  - kv/shared\n");

        // when
        var result = reader.read(dir.toString(), Map.of());

        // then
        assertThat(result.secretsConfig().sources())
                .singleElement()
                .satisfies(source -> assertThat(source.type()).isEqualTo("vault"));
    }

    @Test
    void read_namesASecretsFileWhoseTopLevelIsNotAMappingAndDoesNotQuoteIt() throws IOException {
        // given
        write("secrets.yaml", "- s3cret\n- other\n");

        // then
        assertThatThrownBy(() -> reader.read(dir.toString(), Map.of()))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("secrets.yaml")
                .hasMessageNotContaining("s3cret");
    }

    @Test
    void read_rejectsATokenInATypeNamingTheFileButNotTheEntry() throws IOException {
        // given - taken literally, it would route the file to a type no factory has
        write("secrets.yaml", "type: \"${env.kind}\"\napiKey: s3cret\n");

        // then
        assertThatThrownBy(() -> reader.read(dir.toString(), Map.of()))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("secrets.yaml")
                .hasMessageNotContaining("env.kind");
    }

    @Test
    void read_treatsAnEmptySecretsFileAsAPlaintextOne() throws IOException {
        // given - nothing to recognise and no type: an empty mapping, which the file provider serves as nothing
        write("secrets.yaml", "# nothing yet\n");

        // when
        var result = reader.read(dir.toString(), Map.of());

        // then
        assertThat(result.secretsConfig().sources())
                .extracting(SecretsSource::type)
                .containsExactly("file");
    }

    @Test
    void read_namesASecretsFileThatIsNotYamlAndDoesNotQuoteIt() throws IOException {
        // given - unbalanced quoting; a parser message would echo the line it choked on
        write("secrets.yaml", "apiKey: \"s3cret\n  : [\n");

        // then
        assertThatThrownBy(() -> reader.read(dir.toString(), Map.of()))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("secrets.yaml")
                .hasMessageNotContaining("s3cret");
    }

    @Test
    void read_rejectsATypeWithNoValue() throws IOException {
        // given
        write("secrets.yaml", "type:\napiKey: s3cret\n");

        // then
        assertThatThrownBy(() -> reader.read(dir.toString(), Map.of()))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("secrets.yaml");
    }

    @Test
    void read_rejectsATypeThatIsNotAPlainValue() throws IOException {
        // given
        write("secrets.yaml", "type:\n  name: vault\n");

        // then
        assertThatThrownBy(() -> reader.read(dir.toString(), Map.of()))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("secrets.yaml");
    }

    // ---------- recognition ----------

    @Test
    void read_givesAFileAFactoryRecognisesThatFactorysType() throws IOException {
        // given - an encrypted file: lists in its metadata, no readable type
        write("secrets001.yaml", SOPS_FILE);

        // when
        var result = reader.read(dir.toString(), Map.of());

        // then
        assertThat(result.secretsConfig().sources())
                .singleElement()
                .satisfies(source -> assertThat(source.type()).isEqualTo("sops"));
    }

    @Test
    void read_letsRecognitionBeatAnExplicitType() throws IOException {
        // given - encryption has turned the declared type into ciphertext
        write("secrets001.yaml", "type: ENC[AES256_GCM,data:x1,iv:y,tag:z,type:str]\n" + SOPS_FILE);

        // when
        var result = reader.read(dir.toString(), Map.of());

        // then
        assertThat(result.secretsConfig().sources())
                .singleElement()
                .satisfies(source -> assertThat(source.type()).isEqualTo("sops"));
    }

    @Test
    void read_handsEachFactoryTheFilesWholeContent() throws IOException {
        // given
        var seen = new ArrayList<String>();
        var spy = new SecretsProviderFactory() {
            @Override
            public String type() {
                return "spy";
            }

            @Override
            public SecretsProvider create(Map<String, String> params) {
                throw new UnsupportedOperationException("never created here");
            }

            @Override
            public boolean recognises(String content) {
                seen.add(content);
                return false;
            }
        };
        write("secrets.yaml", "apiKey: s3cret\n");

        // when
        new EnvironmentReader(List.of(spy)).read(dir.toString(), Map.of());

        // then
        assertThat(seen).containsExactly("apiKey: s3cret\n");
    }

    @Test
    void read_rejectsAFileClaimedByFactoriesOfTwoTypesNamingTheFileAndBothTypes() throws IOException {
        // given
        var ambiguous = new EnvironmentReader(List.of(SOPS, recognising("crypt", "sops:")));
        write("secrets001.yaml", SOPS_FILE);

        // then
        assertThatThrownBy(() -> ambiguous.read(dir.toString(), Map.of()))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("secrets001.yaml")
                .hasMessageContaining("sops")
                .hasMessageContaining("crypt");
    }

    @Test
    void read_acceptsAFileClaimedTwiceForTheSameType() throws IOException {
        // given - two claims, one answer: nothing is ambiguous
        var twice = new EnvironmentReader(List.of(SOPS, recognising("sops", "sops:")));
        write("secrets001.yaml", SOPS_FILE);

        // when
        var result = twice.read(dir.toString(), Map.of());

        // then
        assertThat(result.secretsConfig().sources())
                .extracting(SecretsSource::type)
                .containsExactly("sops");
    }

    @Test
    void read_namesTheFactoryAndTheFileWhenRecognisingThrows() throws IOException {
        // given
        var broken = new SecretsProviderFactory() {
            @Override
            public String type() {
                return "broken";
            }

            @Override
            public SecretsProvider create(Map<String, String> params) {
                throw new UnsupportedOperationException("never created here");
            }

            @Override
            public boolean recognises(String content) {
                throw new IllegalStateException("cannot tell");
            }
        };
        write("secrets.yaml", "apiKey: s3cret\n");

        // then
        assertThatThrownBy(() -> new EnvironmentReader(List.of(broken)).read(dir.toString(), Map.of()))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("broken")
                .hasMessageContaining("secrets.yaml");
    }

    @Test
    void read_asksNoFactoryAboutAFileThatIsNotASecretsFile() throws IOException {
        // given - recognition decides a secrets file's type, not whether a file is one
        write("env.yaml", "sops: here\n");
        write("notes.yaml", "sops: here\n");

        // when
        var result = reader.read(dir.toString(), Map.of());

        // then
        assertThat(result.secretsConfig().sources()).isEmpty();
        assertThat(result.env()).containsOnly(entry("sops", "here"));
    }

    @Test
    void constructor_throwsForNullFactoriesOrANullElement() {
        // then
        assertThatThrownBy(() -> new EnvironmentReader(null)).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> new EnvironmentReader(Arrays.asList(SOPS, null)))
                .isInstanceOf(BratException.class);
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
        var result = reader.read(dir.toString(), Map.of());

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
        var result = reader.read(dir.toString(), Map.of());

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
        var result = reader.read(dir.toString(), Map.of());

        // then
        assertThat(result.env()).isEmpty();
        assertThat(result.secretsConfig().sources()).isEmpty();
    }

    @Test
    void read_ignoresUnrecognisedFilesDifferingOnlyInTheirExtension() throws IOException {
        // given - only a name the directory gives meaning to can be ambiguous
        write("notes.yaml", "a: b\n");
        write("notes.yml", "a: c\n");

        // when
        var result = reader.read(dir.toString(), Map.of());

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

    /** A file in the shape SOPS writes: ciphertext values, and a metadata block holding lists. */
    private static final String SOPS_FILE = """
            apiKey: ENC[AES256_GCM,data:abc,iv:def,tag:ghi,type:str]
            sops:
              age:
                - recipient: age1qyqszqgpqyqszqgpqyqszqgpqyqszqgp
                  enc: ENC-AGE-BLOCK
              lastmodified: "2026-10-04T00:00:00Z"
              version: 3.9.0
            """;

    /**
     * A factory that claims every file containing a top-level line starting with {@code marker}.
     *
     * @param type the factory's type
     * @param marker the start of the line that identifies its files
     * @return the factory
     */
    private static SecretsProviderFactory recognising(String type, String marker) {
        return new SecretsProviderFactory() {
            @Override
            public String type() {
                return type;
            }

            @Override
            public SecretsProvider create(Map<String, String> params) {
                throw new UnsupportedOperationException("never created here");
            }

            @Override
            public boolean recognises(String content) {
                return content.lines().anyMatch(line -> line.startsWith(marker));
            }
        };
    }
}
