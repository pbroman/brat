package dev.pbroman.brat.core.launch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.loader.SuiteLoader;
import dev.pbroman.brat.core.secrets.SecretsProviderConfig;
import dev.pbroman.brat.core.util.Require;
import dev.pbroman.brat.core.util.ResourceReader;

import static dev.pbroman.brat.core.launch.LaunchUtils.fileName;
import static dev.pbroman.brat.core.util.Constants.FILE_PREFIX;

/**
 * Reads a {@link Launch} — the suite and the environment it names — into a {@link PreparedRun}.
 * <p>
 * This is where a location given at launch meets the rest of BRAT: both are resolved here, and the
 * suite's resolved location is handed to the environment, so nothing downstream sees a path as it was
 * given and no caller has to join the two itself.
 */
public final class LaunchReader {

    private static final String SUITE = "suite";
    private static final List<String> BRAT_YAML_EXTENSIONS = List.of(".brat.yaml", ".brat.yml");

    private final SuiteLoader loader;
    private final EnvironmentReader environmentReader;

    /**
     * Constructs a reader that loads suites with {@code loader} and reads environment directories with
     * {@code environmentReader} — typically a runner's own, so that what is read is what it can run.
     *
     * @param loader binds a suite document; must not be {@code null}
     * @param environmentReader reads an environment directory; must not be {@code null}
     * @throws BratException if either argument is {@code null}
     */
    public LaunchReader(SuiteLoader loader, EnvironmentReader environmentReader) {
        Require.nonNull(loader, "The suite loader must not be null");
        Require.nonNull(environmentReader, "The environment reader must not be null");
        this.loader = loader;
        this.environmentReader = environmentReader;
    }

    /**
     * Reads {@code launch}: its suite, then its environment.
     * <ol>
     *     <li><strong>The suite.</strong> Its location is resolved as a launch location — a path with
     *         no prefix is on the filesystem, relative to the working directory unless absolute;
     *         {@code file:} and {@code classpath:} are accepted — to a prefixed, absolute one. A
     *         filesystem location that is a <strong>directory</strong> stands for the one suite file
     *         in it — a regular file directly inside whose name ends in {@code .brat.yaml} or
     *         {@code .brat.yml}; subdirectories are not searched. The document is read and loaded, with
     *         the suite file's location as its origin, so a load error names it.</li>
     *     <li><strong>The environment.</strong> By <strong>name</strong>, it is the directory of that
     *         name in the suite file's directory. By <strong>directory</strong>, it is that directory.
     *         Either is read by the {@link EnvironmentReader}, which also rejects a directory holding
     *         no environment file. With neither, an empty {@code env} and a secrets configuration with
     *         no sources, which leaves environment variables as the only secrets. Always carrying the
     *         launch's {@code params}.</li>
     *     <li><strong>The join.</strong> The environment's {@code suiteLocation} is the suite's
     *         resolved location, so a bare path in the suite — a body file, say — resolves next to
     *         the suite file, in the same medium.</li>
     * </ol>
     * Nothing is run, and no file a suite names is read here.
     *
     * @param launch the launch to read; must not be {@code null}
     * @return the suite and its environment, joined
     * @throws BratException if {@code launch} is {@code null}; if the suite location names no valid
     *         path or nothing can be read there — naming it; if it is a directory holding no suite file,
     *         naming the directory, or several, naming the directory and every suite file in it,
     *         sorted, and saying to name one; for anything the loader rejects, with the suite file's
     *         location as the origin; if the launch names an environment by name and the suite is on
     *         the classpath — a classpath directory cannot be listed — or no directory of that name
     *         is beside the suite, naming the name, the suite file's directory and the directories
     *         there holding an environment file, sorted, or saying there are none — a directory that
     *         cannot be listed is left out of that list, and the suite file's directory itself cannot
     *         be listed, naming it; or for anything
     *         reading the environment rejects, {@code ${...}} in a launch parameter and a directory
     *         holding no environment file included. The suite is read first, so a launch wrong in both
     *         reports the suite
     */
    public PreparedRun read(Launch launch) {
        Require.nonNull(launch, "The launch to read must not be null");
        var location = suiteLocation(launch.suite());
        var suite = loader.load(ResourceReader.readFileToString(location), location);
        Environment environment;
        if (launch.environment() != null) {
            environment = environmentReader.read(environmentBeside(location, launch.environment()), launch.params());
        } else if (launch.environmentDirectory() != null) {
            environment = environmentReader.read(launch.environmentDirectory(), launch.params());
        } else {
            environment =
                    new Environment(Map.of(), new LinkedHashMap<>(launch.params()), SecretsProviderConfig.empty());
        }
        return new PreparedRun(suite, environment.withSuiteLocation(location));
    }

    /**
     * Resolves the suite as given to the location of the suite file: unchanged for a file, the one suite
     * file inside for a directory on the filesystem.
     *
     * @param given the suite as given at launch
     * @return the suite file's location, prefixed and absolute
     * @throws BratException as {@link #read(Launch)} says for the suite
     */
    private static String suiteLocation(String given) {
        var location = LaunchLocations.fileLocation(given);
        if (location.startsWith(FILE_PREFIX)) {
            var path = Path.of(location.substring(FILE_PREFIX.length()));
            if (Files.isDirectory(path)) {
                return theSuiteIn(path, given);
            }
        }
        return location;
    }

    /**
     * The one suite file directly in {@code directory}.
     *
     * @param directory the directory, absolute
     * @param given the suite as given at launch, for messages
     * @return the suite file's location, {@code file:}-prefixed and absolute
     * @throws BratException if the directory holds no regular file ending in {@code .brat.yaml} or
     *         {@code .brat.yml}, naming {@code given}; or several, naming {@code given} and each file
     *         name, sorted, and saying to name one; or if it cannot be listed, naming {@code given}
     */
    private static String theSuiteIn(Path directory, String given) {
        var files = LaunchUtils.regularFilesIn(directory, given, SUITE);
        var bratFiles = files.stream().filter(LaunchReader::isBratYaml).toList();
        if (bratFiles.isEmpty()) {
            throw new BratException(
                    String.format("No suite in '%s': a suite file's name ends in .brat.yaml or .brat.yml", given));
        }
        if (bratFiles.size() > 1) {
            var names = bratFiles.stream().map(LaunchUtils::fileName).toList();
            throw new BratException(String.format(
                    "'%s' holds %d suite files: %s — name one, such as '%s'",
                    given, names.size(), String.join(", ", names), directory.resolve(names.getFirst())));
        }
        return FILE_PREFIX + bratFiles.getFirst();
    }

    /**
     * The environment directory named {@code name} beside the suite file.
     *
     * @param suiteLocation the suite file's location, prefixed and absolute
     * @param name the environment's name
     * @return the directory's absolute path, as {@link EnvironmentReader#read} takes it
     * @throws BratException if {@code suiteLocation} is on the classpath; or if the suite file's
     *         directory holds no directory named {@code name} — naming the name, that directory, and
     *         the directories in it holding an environment file
     *         ({@link EnvironmentReader#holdsEnvironmentFile(Path)}), sorted, or saying there are none
     */
    private static String environmentBeside(String suiteLocation, String name) {
        if (!suiteLocation.startsWith(FILE_PREFIX)) {
            throw new BratException(String.format(
                    "The environment '%s' cannot be found beside '%s': a suite on the classpath has no directory "
                            + "that can be listed. Name the environment directory instead",
                    name, suiteLocation));
        }
        // An absolute file path always has a parent, if only the root.
        var suiteDirectory = Objects.requireNonNull(
                Path.of(suiteLocation.substring(FILE_PREFIX.length())).getParent());
        var environment = suiteDirectory.resolve(name);
        if (Files.isDirectory(environment)) {
            return environment.toString();
        }
        var available = environmentsIn(suiteDirectory);
        throw new BratException(String.format(
                "No environment '%s' in '%s' — %s",
                name,
                suiteDirectory,
                available.isEmpty()
                        ? "none of its directories holds an environment file"
                        : "available: " + String.join(", ", available)));
    }

    /**
     * The directories directly in {@code directory} that hold an environment file, by name.
     *
     * @param directory the suite file's directory
     * @return their names, sorted as {@link LaunchUtils#regularFilesIn} sorts files
     * @throws BratException if {@code directory} cannot be listed, naming it
     */
    private static List<String> environmentsIn(Path directory) {
        try (var listing = Files.list(directory)) {
            return listing.filter(Files::isDirectory)
                    .filter(EnvironmentReader::holdsEnvironmentFile)
                    .map(LaunchUtils::fileName)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new BratException("The suite directory '" + directory + "' cannot be listed", e);
        }
    }

    /**
     * Whether {@code file} has a BRAT YAML extension.
     *
     * @param file the file
     * @return whether its name ends in one of the BRAT YAML extensions
     */
    private static boolean isBratYaml(Path file) {
        var name = fileName(file);
        return BRAT_YAML_EXTENSIONS.stream().anyMatch(name::endsWith);
    }
}
