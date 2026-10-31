package dev.pbroman.brat.cli;

import java.io.File;
import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import dev.pbroman.brat.core.api.listener.RunControl;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.launch.Launch;
import dev.pbroman.brat.core.launch.LaunchReader;
import dev.pbroman.brat.core.launch.PreparedRun;
import dev.pbroman.brat.core.reporting.ConsoleRunReporter;
import dev.pbroman.brat.core.runner.Brat;
import org.apache.commons.lang3.StringUtils;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * The {@code brat run} command: runs one suite, reports it on stdout, and answers with an exit code.
 *
 * <pre>
 * brat run &lt;suite-or-directory&gt; [-e &lt;name&gt; | --env-dir &lt;path&gt;] [-p key=value]... [--verbose]
 * </pre>
 *
 * <strong>What it runs.</strong> The arguments map onto a {@code Launch} and nothing more — every rule
 * about suites and environments is core's: the argument is the suite file, or a directory holding
 * exactly one; {@code -e}/{@code --environment} names an environment directory beside the suite;
 * {@code --env-dir} names any environment directory, relative to the working directory or absolute;
 * neither means no environment. {@code -e} and {@code --env-dir} are exclusive. Each
 * {@code -p}/{@code --param key=value} is a launch parameter — {@code ${params.key}}, or a namespace
 * override such as {@code env.baseUrl=…}; a key given twice keeps its last value.
 * <p>
 * <strong>Stdout</strong> carries the run and nothing else: first one line naming what runs —
 * <pre>
 * brat run orders-api/orders.brat.yaml · environment dev
 * </pre>
 * — where the suite is the argument as given, followed by the suite file's name when the argument named
 * a directory, and the environment is the name given to {@code -e}, the path given to {@code --env-dir},
 * or {@code no environment}; then the console report of the run. The line is printed once the launch
 * has been read, so a launch that cannot be read prints nothing on stdout.
 * <p>
 * <strong>Stderr</strong> carries everything else: the command's own errors, each one line beginning
 * {@code brat: } with no stack trace; the lines a Ctrl-C prints; and the log. Nothing written to either
 * stream holds a secret: core's messages never quote one, and the report masks them.
 * <p>
 * <strong>The exit code:</strong>
 * <ul>
 *   <li>{@code 0} — the run passed;</li>
 *   <li>{@code 1} — the run failed: a request or assertion failed, a suite was aborted, or the run was
 *       stopped or cancelled;</li>
 *   <li>{@code 2} — it could not run: arguments picocli rejects (an unknown option, {@code -e} with
 *       {@code --env-dir}, a {@code -p} without {@code =}), a launch that cannot be read (no suite, an
 *       environment that is not there, a suite that does not load), or a structural error that ended
 *       the run. Its message is printed to stderr, the stack trace too with {@code --verbose}. Any
 *       other failure — any exception, or an {@link Error} — exits {@code 2} the same way, its message
 *       naming its type.</li>
 * </ul>
 * <strong>Ctrl-C</strong> stops the run gracefully the first time — open suites run their teardown —
 * immediately the second time, and exits at once the third; see {@link InterruptStopper}.
 * <p>
 * <strong>Logging</strong> goes to stderr through {@code slf4j-simple}, configured by system properties
 * before the run builds anything that logs: {@code org.slf4j.simpleLogger.logFile} is
 * {@code System.err}, {@code org.slf4j.simpleLogger.defaultLogLevel} is {@code warn} — {@code debug}
 * with {@code --verbose} — and {@code org.slf4j.simpleLogger.showThreadName} is {@code false}. A
 * property already set, by {@code -D} on the {@code java} command line, is left as it is.
 */
@Command(name = "run", description = "Runs a suite and reports it.")
final class RunCommand implements Callable<Integer> {

    private static final int PASSED = 0;
    private static final int FAILED = 1;
    private static final int NOT_RUN = 2;

    private static final String SEPARATORS = "/\\";

    private static final String LOG_LEVEL = "org.slf4j.simpleLogger.defaultLogLevel";
    private static final String LOG_FILE = "org.slf4j.simpleLogger.logFile";
    private static final String LOG_THREAD_NAME = "org.slf4j.simpleLogger.showThreadName";

    private final Supplier<RunControl> controls;

    @Spec
    private CommandSpec spec;

    @Parameters(
            index = "0",
            paramLabel = "<suite-or-directory>",
            description = "The suite file, or a directory holding exactly one *.brat.yaml.")
    private String suite;

    @ArgGroup(exclusive = true)
    private EnvironmentChoice environment;

    @Option(
            names = {"-p", "--param"},
            paramLabel = "key=value",
            description = "A launch parameter, ${params.key}, or a namespace override such as env.baseUrl=….")
    private Map<String, String> params = new LinkedHashMap<>();

    @Option(names = "--verbose", description = "Log at DEBUG, and print the stack trace of an error.")
    private boolean verbose;

    /** Constructs the command picocli runs, stopped through a new control per run. */
    RunCommand() {
        this(RunControl::create);
    }

    /**
     * Constructs the command over a source of run controls.
     *
     * @param controls supplies the control each run is stopped through, Ctrl-C included; must not be
     *        {@code null}
     */
    RunCommand(Supplier<RunControl> controls) {
        this.controls = controls;
    }

    @Override
    public Integer call() {
        // Before anything that logs exists: slf4j-simple reads its configuration once, on first use.
        configureLogging();
        try (var brat = Brat.builder().build()) {
            var prepared = new LaunchReader(brat.loader(), brat.environmentReader()).read(launch());
            // The stream core's console reporter writes to, so this line and the report stay in order.
            PrintStream report = System.out;
            report.println(firstLine(prepared));
            report.flush();
            var control = controls.get();
            var stopper = InterruptStopper.install(control, spec.commandLine().getErr());
            try {
                var listeners = List.of(brat.reporter(ConsoleRunReporter.NAME, Map.of()));
                var result = brat.run(prepared.suite(), prepared.environment(), listeners, control);
                return result.failed() ? FAILED : PASSED;
            } finally {
                stopper.close();
            }
        } catch (BratException e) {
            return notRun(e.getMessage(), e);
        } catch (RuntimeException | Error e) {
            // An Error ends the run as surely as an exception, and is no more evidence about the API.
            return notRun(e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    /** Sets the logging properties this command owns, leaving any already set alone. */
    private void configureLogging() {
        setIfAbsent(LOG_LEVEL, verbose ? "debug" : "warn");
        setIfAbsent(LOG_FILE, "System.err");
        setIfAbsent(LOG_THREAD_NAME, "false");
    }

    private static void setIfAbsent(String key, String value) {
        if (System.getProperty(key) == null) {
            System.setProperty(key, value);
        }
    }

    /**
     * The launch the arguments describe.
     *
     * @return the launch
     */
    private Launch launch() {
        var launch = Launch.of(suite).withParams(params);
        if (environment == null) {
            return launch;
        }
        return environment.name != null
                ? launch.withEnvironment(environment.name)
                : launch.withEnvironmentDirectory(environment.directory);
    }

    /**
     * The line naming what runs: the suite as given, plus the file found when it named a directory,
     * and the environment as given.
     *
     * @param prepared the launch, read
     * @return the line
     */
    private String firstLine(PreparedRun prepared) {
        // By string rather than Path: an argument core accepts, such as a file:-prefixed one, need not
        // be a valid path on every platform.
        var fileName = lastSegment(prepared.environment().suiteLocation());
        var given = StringUtils.stripEnd(suite, SEPARATORS);
        String shown;
        if (fileName.equals(lastSegment(given))) {
            shown = suite;
        } else {
            shown = given + File.separator + fileName;
        }
        String shownEnvironment;
        if (environment == null) {
            shownEnvironment = "no environment";
        } else {
            shownEnvironment = "environment " + (environment.name != null ? environment.name : environment.directory);
        }
        return "brat run " + shown + " · " + shownEnvironment;
    }

    /**
     * The part of {@code path} after its last separator, either {@code /} or {@code \}.
     *
     * @param path a path or location
     * @return its last segment; all of it when it holds no separator
     */
    private static String lastSegment(String path) {
        return path.substring(StringUtils.lastIndexOfAny(path, "/", "\\") + 1);
    }

    /**
     * Reports why the run could not happen, and answers its exit code.
     *
     * @param message what went wrong
     * @param cause what was thrown, whose stack trace is printed with {@code --verbose}
     * @return {@code 2}
     */
    private int notRun(String message, Throwable cause) {
        var err = spec.commandLine().getErr();
        err.println("brat: " + message);
        if (verbose) {
            cause.printStackTrace(err);
        }
        err.flush();
        return NOT_RUN;
    }

    /** Where the environment comes from: a name beside the suite, or a directory. Exclusive. */
    private static final class EnvironmentChoice {

        @Option(
                names = {"-e", "--environment"},
                paramLabel = "<name>",
                description = "The environment directory of that name, beside the suite.")
        private String name;

        @Option(
                names = "--env-dir",
                paramLabel = "<path>",
                description = "Any environment directory, relative to here or absolute.")
        private String directory;
    }
}
