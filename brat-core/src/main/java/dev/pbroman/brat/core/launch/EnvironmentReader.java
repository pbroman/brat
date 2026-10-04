package dev.pbroman.brat.core.launch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Pattern;

import dev.pbroman.brat.core.api.secrets.SecretsProviderFactory;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.runner.Environment;
import dev.pbroman.brat.core.secrets.FileSecretsProviderFactory;
import dev.pbroman.brat.core.secrets.FlatYamlLoader;
import dev.pbroman.brat.core.secrets.SecretsProviderConfig;
import dev.pbroman.brat.core.secrets.SecretsProviderConfigLoader;
import dev.pbroman.brat.core.secrets.SecretsSource;
import dev.pbroman.brat.core.util.Require;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

import static dev.pbroman.brat.core.util.Constants.FILE_PREFIX;
import static dev.pbroman.brat.core.util.Constants.TOKEN_PREFIX;

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
 * <p>
 * A reader knows the secrets provider factories of the runner it belongs to, because a secrets file's
 * type may be recognised from its content rather than declared in it — obtain one from
 * {@code Brat.environmentReader()}.
 */
@Slf4j
public final class EnvironmentReader {

    private static final String ENV_FILE = "env";
    private static final String PROVIDERS_FILE = "providers";
    private static final Pattern SECRETS_FILE = Pattern.compile("secrets|secrets[0-9].*");
    private static final List<String> YAML_EXTENSIONS = List.of(".yaml", ".yml");
    private static final YAMLMapper YAML = YAMLMapper.builder().build();

    private final List<SecretsProviderFactory> factories;

    /**
     * Constructs a reader that lets {@code factories} recognise secrets files.
     *
     * @param factories the factories asked whether they recognise a secrets file, possibly empty;
     *        must not be {@code null}. Copied
     * @throws BratException if {@code factories} is {@code null} or holds a {@code null} element
     */
    public EnvironmentReader(Collection<SecretsProviderFactory> factories) {
        Require.nonNull(factories, "The secrets provider factories must not be null");
        if (factories.stream().anyMatch(Objects::isNull)) {
            throw new BratException("The secrets provider factories must not contain null");
        }
        this.factories = List.copyOf(factories);
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
     *         secrets source, with a {@code location} parameter of {@code file:} and its absolute
     *         path. Its type is decided in this order:
     *         <ol>
     *             <li>the type of the factory that {@linkplain SecretsProviderFactory#recognises(String)
     *                 recognises} its content — first, since a format that encrypts its values cannot
     *                 keep a {@code type} entry readable;</li>
     *             <li>otherwise its top-level {@code type} entry. Only the top level is read for it, so
     *                 the rest of the file may be any YAML;</li>
     *             <li>otherwise {@code file}.</li>
     *         </ol></li>
     *     <li>any other {@code .yaml} or {@code .yml} file — logged at WARN, naming it, and
     *         otherwise ignored. This is what catches {@code secret001.yaml} or a committed
     *         {@code secrets.example.yaml}, and it applies to {@code notes.yaml} beside
     *         {@code notes.yml} as to any other.</li>
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
     *             <li>if two recognised files differ only in their extension, as {@code env.yaml}
     *                 and {@code env.yml} do — naming both;</li>
     *             <li>if a recognised file cannot be read or is not a document its reader accepts —
     *                 naming the file and never quoting its content. For a secrets file nobody
     *                 recognises, that is a YAML document whose top level is a mapping — an empty
     *                 one counts as a mapping with no {@code type};</li>
     *             <li>if a secrets file is recognised by factories of two different types, naming the
     *                 file and both types; or if a factory's {@code recognises} throws, naming the
     *                 factory's type and the file;</li>
     *             <li>if a secrets file nobody recognises has a {@code type} entry that is not a plain
     *                 value, is blank, or holds a {@code ${...}} token — naming the file and never
     *                 quoting the entry;</li>
     *             <li>under the conditions {@link Environment}'s constructor rejects, which include a
     *                 {@code ${...}} token in an {@code env} or {@code params} value.</li>
     *         </ul>
     */
    public Environment read(String directory, Map<String, String> params) {
        var path = LaunchLocations.directory(directory);
        Require.nonNull(params, "The params must not be null");
        var yamlFiles = yamlFilesIn(path, directory);

        Map<String, String> env = Map.of();
        var providerConfig = SecretsProviderConfig.empty();
        var sources = new ArrayList<SecretsSource>();
        for (var file : yamlFiles) {
            var name = baseName(file);
            if (ENV_FILE.equals(name)) {
                env = load(file, FlatYamlLoader::load);
            } else if (PROVIDERS_FILE.equals(name)) {
                providerConfig = load(file, SecretsProviderConfigLoader::load);
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
                new LinkedHashMap<>(env), new LinkedHashMap<>(params), providerConfig.withSources(sources));
    }

    /**
     * Lists the YAML files directly in {@code path}, sorted by name, rejecting two that differ only in
     * their extension.
     *
     * @param path the directory
     * @param given the directory as given, for messages
     * @return the YAML files, sorted by file name
     * @throws BratException if {@code path} does not exist, is not a directory or cannot be listed,
     *         or holds two recognised YAML files differing only in their extension
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
            var name = baseName(file);
            var twin = isRecognised(name) ? byName.put(name, file) : null;
            if (twin != null) {
                throw new BratException("The environment directory '" + given + "' holds both '" + twin.getFileName()
                        + "' and '" + file.getFileName() + "'; keep one");
            }
            yamlFiles.add(file);
        }
        return yamlFiles;
    }

    /**
     * Whether a YAML file of this base name means something in an environment directory.
     *
     * @param name the file name without its extension
     * @return whether it is the env file, the providers file or a secrets file
     */
    private static boolean isRecognised(String name) {
        return ENV_FILE.equals(name)
                || PROVIDERS_FILE.equals(name)
                || SECRETS_FILE.matcher(name).matches();
    }

    /**
     * Builds the secrets source a secrets file stands for, deciding its type.
     *
     * @param file the secrets file
     * @return a source of the file's type, located at the file
     * @throws BratException if the file cannot be read, is claimed by factories of two types, a
     *         factory's {@code recognises} throws, or — where nobody claims it — it is not a mapping or
     *         its {@code type} entry is unusable
     */
    private SecretsSource secretsSource(Path file) {
        var content = load(file, Function.identity());
        var type = recognisedType(file, content).orElseGet(() -> declaredType(file, content));
        return new SecretsSource(type, Map.of(FileSecretsProviderFactory.LOCATION_PARAM, FILE_PREFIX + file));
    }

    /**
     * The type of the factory that recognises {@code content}, if one does.
     *
     * @param file the file, for messages
     * @param content its content
     * @return the claiming type, or empty if no factory claims the file
     * @throws BratException if factories of two types claim it, or a factory's {@code recognises}
     *         throws
     */
    private Optional<String> recognisedType(Path file, String content) {
        var claimedBy = new TreeSet<String>();
        for (var factory : factories) {
            boolean claims;
            try {
                claims = factory.recognises(content);
            } catch (RuntimeException e) {
                throw new BratException(
                        "The secrets provider factory for type '" + factory.type() + "' failed to tell whether it "
                                + "recognises the secrets file '" + file + "'",
                        e);
            }
            if (claims) {
                claimedBy.add(factory.type());
            }
        }
        if (claimedBy.size() > 1) {
            throw new BratException("The secrets file '" + file + "' is recognised by the provider types " + claimedBy
                    + "; it must be one");
        }
        return claimedBy.stream().findFirst();
    }

    /**
     * The type a secrets file nobody recognises declares in its top-level {@code type} entry, or
     * {@code file} where it has none. Only the top level is read, so the rest may be any YAML.
     *
     * @param file the file, for messages
     * @param content its content
     * @return the declared type, or {@code file}
     * @throws BratException if the content is not a YAML mapping, or its {@code type} entry is not a
     *         plain value, is blank or holds a token — never quoting either
     */
    private static String declaredType(Path file, String content) {
        JsonNode root;
        try {
            root = YAML.readTree(content);
        } catch (JacksonException e) {
            // The parser's own message quotes the source, which may be a secret.
            throw new BratException("The secrets file '" + file + "' is not a YAML document");
        }
        if (root.isMissingNode()) {
            return FileSecretsProviderFactory.TYPE;
        }
        if (!root.isObject()) {
            throw new BratException("The secrets file '" + file + "' must be a mapping at its top level");
        }
        var type = root.get(FileSecretsProviderFactory.TYPE_KEY);
        if (type == null) {
            return FileSecretsProviderFactory.TYPE;
        }
        if (!type.isValueNode() || type.isNull()) {
            throw new BratException("The 'type' of the secrets file '" + file + "' must be a plain value");
        }
        var text = type.asString();
        if (text.isBlank()) {
            throw new BratException("The secrets file '" + file + "' has a blank type");
        }
        if (text.contains(TOKEN_PREFIX)) {
            throw new BratException("The 'type' of the secrets file '" + file + "' holds a ${...} token, but it is "
                    + "read as written: nothing in a secrets file is resolved");
        }
        return text;
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
