package dev.pbroman.brat.cli;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

import dev.pbroman.brat.core.api.listener.RunControl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

class RunCommandTest {

    private static final String FIXTURES = "src/test/resources/cli";
    private static final String ORDERS = FIXTURES + "/orders";
    private static final String ORDERS_FILE = ORDERS + "/orders.brat.yaml";

    private static final String LEVEL = "org.slf4j.simpleLogger.defaultLogLevel";
    private static final String LOG_FILE = "org.slf4j.simpleLogger.logFile";
    private static final String THREAD_NAME = "org.slf4j.simpleLogger.showThreadName";
    private static final List<String> LOGGING = List.of(LEVEL, LOG_FILE, THREAD_NAME);

    @BeforeEach
    @AfterEach
    void clearLoggingProperties() {
        LOGGING.forEach(System::clearProperty);
    }

    // ---------- exit codes ----------

    @Test
    void run_exitsWith0WhenTheRunPasses() {
        // when
        var result = brat("run", ORDERS, "-e", "dev", "-p", "skip=yes");

        // then
        assertThat(result.exitCode()).isZero();
    }

    @Test
    void run_exitsWith1WhenARequestFails() {
        // when - nothing listens on the environment's port, so the request errors
        var result = brat("run", ORDERS, "-e", "dev");

        // then
        assertThat(result.exitCode()).isEqualTo(1);
    }

    @Test
    void run_exitsWith1WhenTheRunIsStopped() {
        // given - a control stopped before the run starts, as a Ctrl-C at once would leave it
        Supplier<RunControl> stopped = () -> {
            var control = RunControl.create();
            control.stop();
            return control;
        };

        // when
        var result = brat(stopped, "run", ORDERS, "-e", "dev", "-p", "skip=yes");

        // then
        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.out()).contains("CANCELLED");
    }

    @Test
    void run_exitsWith2WhenTheSuiteIsNotThere() {
        // when
        var result = brat("run", FIXTURES + "/nosuch.brat.yaml");

        // then - nothing ran, so nothing is reported
        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.err()).startsWith("brat: ").contains("nosuch.brat.yaml");
        assertThat(result.out()).isEmpty();
    }

    @Test
    void run_exitsWith2WhenTheDirectoryHoldsSeveralSuites() {
        // when
        var result = brat("run", FIXTURES + "/two");

        // then
        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.err()).contains("a.brat.yaml", "b.brat.yaml");
    }

    @Test
    void run_exitsWith2WhenTheEnvironmentIsNotThere() {
        // when
        var result = brat("run", ORDERS, "-e", "prod");

        // then - the message names what is there instead
        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.err()).contains("prod", "dev");
        assertThat(result.out()).isEmpty();
    }

    @Test
    void run_exitsWith2WhenTheSuiteDoesNotLoad() {
        // when
        var result = brat("run", FIXTURES + "/broken.brat.yaml");

        // then
        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.err()).contains("responsActions");
    }

    @Test
    void run_exitsWith2WhenBothEnvironmentFormsAreGiven() {
        // when
        var result = brat("run", ORDERS, "-e", "dev", "--env-dir", FIXTURES + "/envs/staging");

        // then
        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.out()).isEmpty();
    }

    @Test
    void run_exitsWith2ForAParamWithoutAnEqualsSign() {
        // when
        var result = brat("run", ORDERS, "-e", "dev", "-p", "skip");

        // then
        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.out()).isEmpty();
    }

    @Test
    void run_exitsWith2AndNamesTheTypeOfAnUnexpectedException() {
        // given - a defect rather than a BratException
        Supplier<RunControl> broken = () -> new RunControl() {
            @Override
            public void stop() {
                // never called
            }

            @Override
            public void cancel() {
                // never called
            }

            @Override
            public boolean isStopping() {
                throw new IllegalStateException("boom");
            }

            @Override
            public boolean isCancelled() {
                throw new IllegalStateException("boom");
            }
        };

        // when
        var result = brat(broken, "run", ORDERS, "-e", "dev", "-p", "skip=yes");

        // then
        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.err()).contains("brat: ", "IllegalStateException", "boom");
    }

    @Test
    void run_exitsWith2AndNamesTheTypeOfAnError() {
        // given - an Error rather than an exception: no more evidence about the API than a defect is
        Supplier<RunControl> broken = () -> new RunControl() {
            @Override
            public void stop() {
                // never called
            }

            @Override
            public void cancel() {
                // never called
            }

            @Override
            public boolean isStopping() {
                throw new AssertionError("broken double");
            }

            @Override
            public boolean isCancelled() {
                throw new AssertionError("broken double");
            }
        };

        // when
        var result = brat(broken, "run", ORDERS, "-e", "dev", "-p", "skip=yes");

        // then
        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.err()).contains("brat: ", "AssertionError", "broken double");
    }

    // ---------- stderr ----------

    @Test
    void run_printsAnErrorAsOneLineWithoutAStackTrace() {
        // when
        var result = brat("run", FIXTURES + "/nosuch.brat.yaml");

        // then
        assertThat(result.err().lines().filter(line -> !line.isBlank())).hasSize(1);
        assertThat(result.err()).doesNotContain("\tat ");
    }

    @Test
    void run_printsTheStackTraceOfAnErrorWithVerbose() {
        // when
        var result = brat("run", FIXTURES + "/nosuch.brat.yaml", "--verbose");

        // then
        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.err()).startsWith("brat: ").contains("\tat ");
    }

    // ---------- stdout ----------

    @Test
    void run_namesTheSuiteFileFoundInADirectoryAndTheEnvironmentFirst() {
        // when
        var result = brat("run", ORDERS, "-e", "dev", "-p", "skip=yes");

        // then
        assertThat(result.out().lines().findFirst())
                .contains("brat run " + Path.of(ORDERS).resolve("orders.brat.yaml") + " · environment dev");
    }

    @Test
    void run_namesTheSuiteFileFoundInAPrefixedOrSlashTerminatedDirectory() {
        // when
        var prefixed = brat("run", "file:" + ORDERS, "-e", "dev", "-p", "skip=yes");
        var slashed = brat("run", ORDERS + "/", "-e", "dev", "-p", "skip=yes");

        // then
        assertThat(prefixed.out().lines().findFirst())
                .contains("brat run file:" + ORDERS + File.separator + "orders.brat.yaml · environment dev");
        assertThat(slashed.out().lines().findFirst())
                .contains("brat run " + ORDERS + File.separator + "orders.brat.yaml · environment dev");
    }

    @Test
    void run_namesASuiteFileAsGivenAndNoEnvironment() {
        // when
        var result = brat("run", ORDERS_FILE, "-p", "skip=yes");

        // then
        assertThat(result.out().lines().findFirst()).contains("brat run " + ORDERS_FILE + " · no environment");
    }

    @Test
    void run_namesAnEnvironmentDirectoryAsGiven() {
        // when
        var result = brat("run", ORDERS_FILE, "--env-dir", FIXTURES + "/envs/staging", "-p", "skip=yes");

        // then
        assertThat(result.out().lines().findFirst())
                .contains("brat run " + ORDERS_FILE + " · environment " + FIXTURES + "/envs/staging");
    }

    @Test
    void run_printsTheReportAfterTheFirstLine() {
        // when
        var result = brat("run", ORDERS, "-e", "dev", "-p", "skip=yes");

        // then
        var lines = result.out().lines().toList();
        assertThat(lines.getFirst()).startsWith("brat run ");
        assertThat(lines.subList(1, lines.size())).anyMatch(line -> line.contains("SKIP   read an order"));
        assertThat(lines.getLast()).endsWith("PASSED");
    }

    @Test
    void run_passesANamespacedParamAsAnOverride() {
        // when
        var result = brat("run", ORDERS, "-e", "dev", "-p", "env.baseUrl=http://127.0.0.1:2");

        // then - the request as sent, printed under the error, went to the overriding address
        assertThat(result.out()).contains("http://127.0.0.1:2/orders/1");
    }

    @Test
    void run_keepsTheLastValueOfARepeatedParam() {
        // when
        var result = brat("run", ORDERS, "-e", "dev", "-p", "skip=no", "-p", "skip=yes");

        // then
        assertThat(result.exitCode()).isZero();
    }

    // ---------- logging ----------

    @Test
    void run_logsAtWarnToStderrByDefault() {
        // when
        brat("run", ORDERS, "-e", "dev", "-p", "skip=yes");

        // then
        assertThat(System.getProperty(LEVEL)).isEqualTo("warn");
        assertThat(System.getProperty(LOG_FILE)).isEqualTo("System.err");
        assertThat(System.getProperty(THREAD_NAME)).isEqualTo("false");
    }

    @Test
    void run_logsAtDebugWithVerbose() {
        // when
        brat("run", ORDERS, "-e", "dev", "-p", "skip=yes", "--verbose");

        // then
        assertThat(System.getProperty(LEVEL)).isEqualTo("debug");
    }

    @Test
    void run_leavesALoggingPropertyThatIsAlreadySet() {
        // given - as -D on the java command line would
        System.setProperty(LEVEL, "error");

        // when
        brat("run", ORDERS, "-e", "dev", "-p", "skip=yes", "--verbose");

        // then
        assertThat(System.getProperty(LEVEL)).isEqualTo("error");
        assertThat(System.getProperty(LOG_FILE)).isEqualTo("System.err");
    }

    // ---------- helpers ----------

    private static Result brat(String... args) {
        return brat(RunControl::create, args);
    }

    /**
     * Runs the command in-process with stdout and stderr captured, the run's controls drawn from
     * {@code controls}.
     */
    private static Result brat(Supplier<RunControl> controls, String... args) {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        var originalOut = System.out;
        var originalErr = System.err;
        System.setOut(new PrintStream(out, true, UTF_8));
        System.setErr(new PrintStream(err, true, UTF_8));
        try {
            CommandLine.IFactory factory = new CommandLine.IFactory() {
                @Override
                public <K> K create(Class<K> type) throws Exception {
                    return type == RunCommand.class
                            ? type.cast(new RunCommand(controls))
                            : CommandLine.defaultFactory().create(type);
                }
            };
            var commandLine = new CommandLine(new BratCli(), factory);
            commandLine.setOut(new PrintWriter(System.out, true));
            commandLine.setErr(new PrintWriter(System.err, true));
            var exitCode = commandLine.execute(args);
            System.out.flush();
            System.err.flush();
            return new Result(exitCode, out.toString(UTF_8), err.toString(UTF_8));
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    private record Result(int exitCode, String out, String err) {}
}
