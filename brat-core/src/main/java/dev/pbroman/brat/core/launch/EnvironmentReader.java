package dev.pbroman.brat.core.launch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.runner.Environment;
import dev.pbroman.brat.core.secrets.FileSecretsProviderFactory;
import dev.pbroman.brat.core.secrets.FlatYamlLoader;
import dev.pbroman.brat.core.secrets.SecretsProviderConfig;
import dev.pbroman.brat.core.secrets.SecretsProviderConfigLoader;
import dev.pbroman.brat.core.secrets.SecretsSource;
import dev.pbroman.brat.core.util.Require;
import lombok.extern.slf4j.Slf4j;

/**
 * Reads an environment directory into the {@link Environment} a run is launched against.
 * <p>
 * An environment directory holds what changes between the systems a suite runs against — the
 * {@code env} values, the secrets sources, how to reach their backends — so that switching
 * environment is switching directory and the suite never changes. Each file is recognised by its
 * exact name:
 * <table>
 *     <caption>What each file in the directory is</caption>
 *     <tr><th>file</th><th>becomes</th></tr>
 *     <tr><td>{@code env.yaml}</td><td>the {@code env} namespace</td></tr>
 *     <tr><td>{@code providers.yaml}</td><td>the secrets providers' parameters</td></tr>
 *     <tr><td>{@code secrets.yaml}, and {@code secrets} followed by a digit and anything else</td>
 *         <td>one secrets source each</td></tr>
 * </table>
 * Each may be spelled {@code .yml} instead.
 */
@Slf4j
public final class EnvironmentReader {

    private static final String ENV_FILE = "env";
    private static final String PROVIDERS_FILE = "providers";
    private static final Pattern SECRETS_FILE = Pattern.compile("secrets|secrets[0-9].*");
    private static final List<String> YAML_EXTENSIONS = List.of(".yaml", ".yml");

    private EnvironmentReader() {
        // utility class
    }

    /**
     * Reads {@code directory} and returns the environment it describes, carrying {@code params}.
     * <p>
     * <strong>What is read.</strong> The regular files directly in the directory — it is not
     * recursed into, and a subdirectory is ignored whatever its name. Names are matched exactly and
     * case-sensitively, each with either a {@code .yaml} or a {@code .yml} extension:
     * <ul>
     *     <li>{@code env} — read as a flat document ({@code FlatYamlLoader}): nested keys become
     *         dotted, every value is text as written. It is the environment's {@code env}; without
     *         one, {@code env} is empty.</li>
     *     <li>{@code providers} — read as a provider configuration
     *         ({@code SecretsProviderConfigLoader}); without one, no type has parameters.</li>
     *     <li>{@code secrets}, or {@code secrets} followed by a digit and then anything — each one
     *         secrets source, of the type its own {@code type} entry names, or {@code file} where it
     *         has none, with a {@code location} parameter of {@code file:} and its absolute path.</li>
     *     <li>any other {@code .yaml} or {@code .yml} file — logged at WARN, naming it, and
     *         otherwise ignored. This is what catches {@code secret001.yaml} or a committed
     *         {@code secrets.example.yaml}.</li>
     *     <li>any other file — ignored.</li>
     * </ul>
     * <strong>Order.</strong> The secrets sources are in the order of their file names compared by
     * {@link String#compareTo(String)} — never by locale — so uppercase sorts before lowercase,
     * {@code secrets.yaml} before {@code secrets001.yaml}, and {@code secrets10.yaml} before
     * {@code secrets9.yaml}. The order is the chain's priority: the first source holding a key wins.
     * <p>
     * The returned environment has no suite location; whoever read the suite attaches it.
     *
     * @param directory the environment directory as given at launch: a path
     *        relative to the working directory, an absolute one, or either behind {@code file:}; must
     *        not be {@code null} or blank
     * @param params the {@code params} namespace for the run, possibly empty; must not be
     *        {@code null}. Not modified
     * @return the environment: {@code env} from the env file, {@code params} as given, and a secrets
     *         configuration holding the provider file's parameters and one source per secrets file,
     *         in order
     * @throws BratException
     *         <ul>
     *             <li>if {@code directory} is {@code null} or blank, carries {@code classpath:},
     *                 does not exist, is not a directory or cannot be listed — naming it;</li>
     *             <li>if {@code params} is {@code null};</li>
     *             <li>if two files differ only in their extension, as {@code env.yaml} and
     *                 {@code env.yml} do — naming both;</li>
     *             <li>if a recognised file cannot be read or is not a document its reader accepts,
     *                 or a secrets file's {@code type} entry is blank — naming the file and never
     *                 quoting its content;</li>
     *             <li>under the conditions {@link Environment}'s constructor rejects, which include a
     *                 {@code ${...}} token in an {@code env} or {@code params} value.</li>
     *         </ul>
     */
    public static Environment read(String directory, Map<String, String> params) {
        var path = LaunchLocations.directory(directory);
        Require.nonNull(params, "The params must not be null");
        var yamlFiles = yamlFilesIn(path, directory);

        Map<String, String> env = Map.of();
        var providerParams = SecretsProviderConfig.empty();
        var sources = new ArrayList<SecretsSource>();
        for (var file : yamlFiles) {
            var name = baseName(file);
            if (ENV_FILE.equals(name)) {
                env = load(file, FlatYamlLoader::load);
            } else if (PROVIDERS_FILE.equals(name)) {
                providerParams = load(file, SecretsProviderConfigLoader::load);
            } else if (SECRETS_FILE.matcher(name).matches()) {
                sources.add(secretsSource(file));
            } else {
                log.warn(
                        "Ignoring '{}' in the environment directory '{}': it is not a file an environment "
                                + "directory holds",
                        file.getFileName(),
                        directory);
            }
        }
        return new Environment(
                new LinkedHashMap<>(env), new LinkedHashMap<>(params), providerParams.withSources(sources));
    }

    /**
     * Lists the YAML files directly in {@code path}, sorted by name, rejecting two that differ only in
     * their extension.
     *
     * @param path the directory
     * @param given the directory as given, for messages
     * @return the YAML files, sorted by file name
     * @throws BratException if {@code path} does not exist, is not a directory or cannot be listed,
     *         or holds two YAML files differing only in their extension
     */
    private static List<Path> yamlFilesIn(Path path, String given) {
        if (!Files.exists(path)) {
            throw new BratException("The environment directory '" + given + "' does not exist (" + path + ")");
        }
        if (!Files.isDirectory(path)) {
            throw new BratException("The environment directory '" + given + "' is not a directory (" + path + ")");
        }
        List<Path> files;
        try (var listing = Files.list(path)) {
            files = listing.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(EnvironmentReader::fileName))
                    .toList();
        } catch (IOException e) {
            throw new BratException("The environment directory '" + given + "' cannot be listed", e);
        }

        var yamlFiles = new ArrayList<Path>();
        var byName = new HashMap<String, Path>();
        for (var file : files) {
            if (!isYaml(file)) {
                log.debug("Ignoring '{}' in the environment directory '{}'", file.getFileName(), given);
                continue;
            }
            var twin = byName.put(baseName(file), file);
            if (twin != null) {
                throw new BratException("The environment directory '" + given + "' holds both '" + twin.getFileName()
                        + "' and '" + file.getFileName() + "'; keep one");
            }
            yamlFiles.add(file);
        }
        return yamlFiles;
    }

    /**
     * Builds the secrets source a secrets file stands for, reading the file for its {@code type}.
     *
     * @param file the secrets file
     * @return a source of the file's type, located at the file
     * @throws BratException if the file cannot be read or parsed, or its {@code type} is blank
     */
    private static SecretsSource secretsSource(Path file) {
        var type = load(file, FlatYamlLoader::load)
                .getOrDefault(FileSecretsProviderFactory.TYPE_KEY, FileSecretsProviderFactory.TYPE);
        if (type.isBlank()) {
            throw new BratException("The secrets file '" + file + "' has a blank type");
        }
        return new SecretsSource(type, Map.of(FileSecretsProviderFactory.LOCATION_PARAM, "file:" + file));
    }

    /**
     * Reads {@code file} and hands its content to {@code loader}, naming the file in any failure.
     *
     * @param file the file to read
     * @param loader what parses the content
     * @param <T> what the loader produces
     * @return what the loader produced
     * @throws BratException if the file cannot be read, or the loader rejects its content — whose own
     *         messages never quote it
     */
    private static <T> T load(Path file, Function<String, T> loader) {
        try {
            return loader.apply(Files.readString(file));
        } catch (IOException e) {
            throw new BratException("The file '" + file + "' cannot be read", e);
        } catch (BratException e) {
            throw new BratException("The file '" + file + "' cannot be used: " + e.getMessage(), e);
        }
    }

    /**
     * Whether {@code file} has a YAML extension.
     *
     * @param file the file
     * @return whether its name ends in one of the YAML extensions
     */
    private static boolean isYaml(Path file) {
        var name = fileName(file);
        return YAML_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    /**
     * The file's name without its extension.
     *
     * @param file a YAML file
     * @return its name up to the last dot
     */
    private static String baseName(Path file) {
        var name = fileName(file);
        return name.substring(0, name.lastIndexOf('.'));
    }

    /**
     * The file's own name, without its directory.
     *
     * @param file a file listed from a directory, which always has a name
     * @return its name
     */
    private static String fileName(Path file) {
        return String.valueOf(file.getFileName());
    }
}
