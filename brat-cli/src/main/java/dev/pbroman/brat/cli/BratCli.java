package dev.pbroman.brat.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * The {@code brat} command: the entry point of the self-contained jar, and the parent of its
 * subcommands.
 * <p>
 * Given no subcommand, it prints its usage to stderr and exits with {@code 2}, the exit code of every
 * usage error. {@code --help} prints the usage to stdout and {@code --version} the version, each
 * exiting with {@code 0}.
 * <p>
 * <strong>Its usage is complete</strong>: after the one-line list of commands, it prints the full
 * usage of every subcommand but {@code help} — each one's arguments, options, exit codes and footer —
 * so {@code brat --help} shows every flag there is. {@code brat help <command>} and
 * {@code brat <command> --help} print one command's usage to stdout and exit with {@code 0}.
 */
@Command(
        name = "brat",
        mixinStandardHelpOptions = true,
        versionProvider = BratVersion.class,
        description = "The Better REST-API Tester.",
        subcommands = {RunCommand.class, HelpCommand.class})
public final class BratCli implements Callable<Integer> {

    private CommandSpec spec;

    /**
     * Receives this command's model as it is built, and follows its command list with the full usage
     * of every subcommand but {@code help}.
     *
     * @param spec this command's model
     */
    @Spec
    void spec(CommandSpec spec) {
        this.spec = spec;
        spec.usageMessage().sectionMap().put(CommandLine.Model.UsageMessageSpec.SECTION_KEY_COMMAND_LIST, help -> {
            var usages = new StringBuilder(help.commandList());
            for (var subcommand : help.subcommands().values()) {
                if (!(subcommand.commandSpec().userObject() instanceof HelpCommand)) {
                    usages.append(System.lineSeparator())
                            .append(subcommand.commandSpec().commandLine().getUsageMessage(help.colorScheme()));
                }
            }
            return usages.toString();
        });
    }

    /**
     * Runs the command with {@code args} and exits the JVM with its exit code.
     *
     * @param args the command line, without the program name
     */
    public static void main(String[] args) {
        System.exit(new CommandLine(new BratCli()).execute(args));
    }

    @Override
    public Integer call() {
        spec.commandLine().usage(spec.commandLine().getErr());
        return CommandLine.ExitCode.USAGE;
    }
}
