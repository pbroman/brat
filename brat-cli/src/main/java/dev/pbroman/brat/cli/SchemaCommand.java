package dev.pbroman.brat.cli;

import java.util.concurrent.Callable;

import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.schema.SuiteSchema;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * The {@code brat schema} command: prints the JSON Schema of a suite file, for pointing an editor at.
 *
 * <pre>
 * brat schema &gt; brat-suite.schema.json
 * </pre>
 *
 * <strong>Stdout</strong> carries the schema exactly as core ships it, and nothing else, so redirecting
 * it gives the file. It takes no arguments and no options but {@code -h}/{@code --help}, which prints
 * its usage to stdout instead and exits with {@code 0}.
 * <p>
 * <strong>The exit code</strong> is {@code 0} once the schema is printed, and {@code 2} for an
 * argument it does not take, or if the schema cannot be read — which only a broken build produces;
 * then a line beginning {@code brat: } goes to stderr and nothing to stdout.
 */
@Command(
        name = "schema",
        description = "Prints the JSON Schema of a suite file, for your editor.",
        exitCodeListHeading = "%nExit codes:%n",
        exitCodeList = {"0:the schema was printed", "2:bad arguments, or the schema could not be read"},
        footer = {"", "Save it with: brat schema > brat-suite.schema.json"})
final class SchemaCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Option(
            names = {"-h", "--help"},
            usageHelp = true,
            description = "Show this help message and exit.")
    private boolean help;

    @Override
    public Integer call() {
        String json;
        try {
            json = SuiteSchema.json();
        } catch (BratException e) {
            var err = spec.commandLine().getErr();
            err.println("brat: " + e.getMessage());
            err.flush();
            return CommandLine.ExitCode.USAGE;
        }
        var out = spec.commandLine().getOut();
        out.print(json);
        out.flush();
        return CommandLine.ExitCode.OK;
    }
}
