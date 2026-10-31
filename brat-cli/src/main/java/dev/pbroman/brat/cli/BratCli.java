package dev.pbroman.brat.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * The {@code brat} command: the entry point of the self-contained jar, and the parent of its
 * subcommands.
 * <p>
 * Given no subcommand, it prints its usage to stderr and exits with {@code 2}, the exit code of every
 * usage error. {@code --help} prints the usage to stdout and {@code --version} the version, each
 * exiting with {@code 0}.
 */
@Command(
        name = "brat",
        mixinStandardHelpOptions = true,
        versionProvider = BratVersion.class,
        description = "The Better REST-API Tester.",
        subcommands = RunCommand.class)
public final class BratCli implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

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
