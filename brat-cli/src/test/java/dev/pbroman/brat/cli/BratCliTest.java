package dev.pbroman.brat.cli;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

class BratCliTest {

    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();
    private CommandLine underTest;

    @BeforeEach
    void setUp() {
        underTest = new CommandLine(new BratCli());
        underTest.setOut(new PrintWriter(out));
        underTest.setErr(new PrintWriter(err));
    }

    @Test
    void execute_printsTheUsageToStderrAndExitsWith2WithoutASubcommand() {
        // when
        var exitCode = underTest.execute();

        // then
        assertThat(exitCode).isEqualTo(2);
        assertThat(err.toString()).contains("Usage: brat");
        assertThat(out.toString()).isEmpty();
    }

    @Test
    void execute_printsTheUsageToStdoutAndExitsWith0ForHelp() {
        // when
        var exitCode = underTest.execute("--help");

        // then
        assertThat(exitCode).isZero();
        assertThat(out.toString()).contains("Usage: brat");
        assertThat(err.toString()).isEmpty();
    }

    @Test
    void execute_exitsWith2ForAnUnknownOption() {
        // when
        var exitCode = underTest.execute("--nosuch");

        // then
        assertThat(exitCode).isEqualTo(2);
        assertThat(err.toString()).contains("--nosuch");
    }

    @Test
    void execute_printsTheVersionAndExitsWith0() {
        // when - run from classes rather than the jar, there is no manifest version
        var exitCode = underTest.execute("--version");

        // then
        assertThat(exitCode).isZero();
        assertThat(out.toString()).startsWith("brat (development build)");
    }

    @Test
    void execute_listsTheRunSubcommandInItsUsage() {
        // when
        underTest.execute("--help");

        // then
        assertThat(out.toString()).contains("run", "Runs a suite and reports it.");
    }
}
