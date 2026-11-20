package dev.pbroman.brat.cli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.Callable;

import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.secrets.SecretsUtils;
import dev.pbroman.brat.openapi.ImportOptions;
import dev.pbroman.brat.openapi.OpenApiImporter;
import dev.pbroman.brat.openapi.Scaffold;
import dev.pbroman.brat.openapi.ScaffoldWriter;
import org.apache.commons.lang3.StringUtils;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * The {@code brat import} command: turns an OpenAPI 3.0 or 3.1 spec into a starting-point suite, its
 * bodies and one environment directory per server.
 *
 * <pre>
 * brat import orders-openapi.yaml --out orders-api
 * </pre>
 *
 * The spec is a file, JSON or YAML, read as UTF-8. {@code --out} names the directory to write, which
 * must not exist or be empty; without it, the directory is the one the import suggests, in the working
 * directory. {@code --body-file-lines} is how many lines a JSON body may have and stay in the suite,
 * {@code 10} unless given.
 * <p>
 * <strong>Stdout</strong>, once everything is written, is a line {@code brat import <spec> → <out>},
 * both as given (or {@code <out>} as suggested), then one line {@code   wrote <out>/<path>} per file in
 * the order written, {@code <path>} with {@code /} on every platform; the suite's line is followed by
 * three spaces and its counts — {@code (5 requests in 2 sub-suites, 1 setup request)}, each part but
 * the requests only where it is not zero, each noun singular for one — and then what to do next:
 * <pre>
 * next: fill in local/env.yaml, set BRAT_SECRET_CLIENT_SECRET, then
 *   brat run orders-api -e local
 * </pre>
 * {@code fill in} names, in the order of the import's notes, each file a note that asks for no secret
 * points at, leaving out the environment files of every environment but the first; {@code set} names
 * the environment variable of each secret a note asks for; either part is left out when it names
 * nothing, and with both left out the line is {@code next: brat run <out> -e <first environment>}.
 * {@code -h}/{@code --help} prints the usage to stdout instead and exits with {@code 0}.
 * <p>
 * <strong>The exit code</strong> is {@code 0} once every file is written, and {@code 2} for bad
 * arguments — a missing spec argument, a negative {@code --body-file-lines} — for a spec that cannot
 * be read, that is not UTF-8, or that the import refuses, and for a directory the import will not
 * write into, or a file it cannot write. Then a line beginning {@code brat: } goes to stderr, and nothing to stdout.
 */
@Command(
        name = "import",
        description = "Imports an OpenAPI 3 spec as a starting-point suite.",
        exitCodeListHeading = "%nExit codes:%n",
        exitCodeList = {
            "0:the suite and its files were written",
            "2:bad arguments, a spec it cannot import, or a directory it will not write into"
        },
        footer = {"", "Then fill in what the comments ask for and: brat run <out> -e <environment>"})
final class ImportCommand implements Callable<Integer> {

    private final Path workingDirectory;

    @Spec
    private CommandSpec spec;

    @Parameters(index = "0", paramLabel = "<spec>", description = "The OpenAPI 3.0 or 3.1 spec, JSON or YAML.")
    private Path specFile;

    @Option(
            names = "--out",
            paramLabel = "<dir>",
            description =
                    "The directory to write; it must not exist, or be empty. Default: named after the spec's title.")
    private String out;

    @Option(
            names = "--body-file-lines",
            paramLabel = "<n>",
            description =
                    "A JSON body longer than this many lines goes to a file of its own. Default: ${DEFAULT-VALUE}.")
    private int bodyFileLines = ImportOptions.DEFAULT_BODY_FILE_LINES;

    @Option(
            names = {"-h", "--help"},
            usageHelp = true,
            description = "Show this help message and exit.")
    private boolean help;

    /** Constructs the command, resolving relative paths against the process's working directory. */
    ImportCommand() {
        this(Path.of(""));
    }

    /**
     * Constructs the command, resolving the spec and the output directory against
     * {@code workingDirectory} where they are relative.
     *
     * @param workingDirectory the directory relative paths start from
     */
    ImportCommand(Path workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    @Override
    public Integer call() {
        Scaffold scaffold;
        String outName;
        try {
            var options = new ImportOptions(bodyFileLines);
            var content = read(workingDirectory.resolve(specFile));
            var fileName = specFile.getFileName();
            var sourceName = fileName == null ? null : fileName.toString();
            scaffold = OpenApiImporter.scaffold(content, sourceName, options);
            outName = out == null ? scaffold.directory() : out;
            ScaffoldWriter.write(scaffold, workingDirectory.resolve(outName));
        } catch (BratException e) {
            var err = spec.commandLine().getErr();
            err.println("brat: " + e.getMessage());
            err.flush();
            return CommandLine.ExitCode.USAGE;
        }
        var printed = StringUtils.stripEnd(outName, "/\\");
        var stdout = spec.commandLine().getOut();
        stdout.println("brat import " + specFile + " → " + printed);
        for (var path : scaffold.files().keySet()) {
            var line = "  wrote " + printed + "/" + path;
            stdout.println(path.equals(scaffold.suiteFile()) ? line + "   (" + counts(scaffold) + ")" : line);
        }
        stdout.print(next(scaffold, printed));
        stdout.flush();
        return CommandLine.ExitCode.OK;
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (MalformedInputException e) {
            throw new BratException("The spec " + file + " is not UTF-8 text; save it as UTF-8 and import it again");
        } catch (IOException | UncheckedIOException e) {
            throw new BratException("Could not read the spec " + file + ": "
                    + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : " " + e.getMessage()));
        }
    }

    private static String counts(Scaffold scaffold) {
        var counts = plural(scaffold.requests(), "request");
        if (scaffold.subSuites() > 0) {
            counts += " in " + plural(scaffold.subSuites(), "sub-suite");
        }
        if (scaffold.setupRequests() > 0) {
            counts += ", " + plural(scaffold.setupRequests(), "setup request");
        }
        return counts;
    }

    private static String plural(int count, String noun) {
        return count + " " + noun + (count == 1 ? "" : "s");
    }

    /** The lines saying what to do next: what to fill in and set, then how to run the suite. */
    private static String next(Scaffold scaffold, String out) {
        var environments = scaffold.environments();
        var first = environments.getFirst();
        var fillIn = new LinkedHashSet<String>();
        var set = new LinkedHashSet<String>();
        for (var note : scaffold.notes()) {
            if (note.secret() != null) {
                set.add(SecretsUtils.envVarName(SecretsUtils.DEFAULT_ENV_PREFIX, note.secret()));
            } else if (!inOtherEnvironment(note.file(), environments, first)) {
                fillIn.add(note.file());
            }
        }
        var parts = new ArrayList<String>();
        if (!fillIn.isEmpty()) {
            parts.add("fill in " + String.join(", ", fillIn));
        }
        if (!set.isEmpty()) {
            parts.add("set " + String.join(", ", set));
        }
        var run = "brat run " + out + " -e " + first;
        var newline = System.lineSeparator();
        return parts.isEmpty()
                ? "next: " + run + newline
                : "next: " + String.join(", ", parts) + ", then" + newline + "  " + run + newline;
    }

    private static boolean inOtherEnvironment(String file, List<String> environments, String first) {
        for (var environment : environments) {
            if (!environment.equals(first) && file.startsWith(environment + "/")) {
                return true;
            }
        }
        return false;
    }
}
