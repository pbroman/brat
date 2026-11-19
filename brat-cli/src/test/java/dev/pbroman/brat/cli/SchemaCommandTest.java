package dev.pbroman.brat.cli;

import java.io.PrintWriter;
import java.io.StringWriter;

import dev.pbroman.brat.core.schema.SuiteSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaCommandTest {

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
    void call_printsTheSchemaExactlyAsCoreShipsItAndExitsWith0() {
        // when
        var exitCode = underTest.execute("schema");

        // then
        assertThat(exitCode).isZero();
        assertThat(out.toString()).isEqualTo(SuiteSchema.json());
        assertThat(err.toString()).isEmpty();
    }

    @Test
    void call_exitsWith2ForAnArgumentItDoesNotTake() {
        // when
        var exitCode = underTest.execute("schema", "orders.brat.yaml");

        // then
        assertThat(exitCode).isEqualTo(2);
        assertThat(out.toString()).isEmpty();
    }

    @Test
    void call_printsItsUsageToStdoutForHelp() {
        // when
        var exitCode = underTest.execute("schema", "--help");

        // then
        assertThat(exitCode).isZero();
        assertThat(out.toString()).contains("Usage: brat schema", "brat schema > brat-suite.schema.json");
    }

    @Test
    void call_isListedInTheUsageOfBrat() {
        // when
        underTest.execute("--help");

        // then
        assertThat(out.toString()).contains("schema", "Prints the JSON Schema of a suite file");
    }
}
