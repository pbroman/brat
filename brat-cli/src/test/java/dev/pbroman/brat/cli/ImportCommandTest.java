package dev.pbroman.brat.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

class ImportCommandTest {

    /** Two servers, client credentials, a tagged operation and one constant without an example. */
    private static final String SPEC = """
            openapi: 3.0.3
            info:
              title: Orders API
            servers:
              - url: http://localhost:8080
                description: local
              - url: https://orders.staging.example.com
                description: staging
            security:
              - clientCredentials: [orders.read]
            components:
              securitySchemes:
                clientCredentials:
                  type: oauth2
                  flows:
                    clientCredentials:
                      tokenUrl: https://login.example.com/oauth/token
                      scopes: {}
            paths:
              /orders/{orderId}:
                get:
                  tags: [orders]
                  summary: read an order
                  responses:
                    "200": { description: the order }
                post:
                  tags: [orders]
                  summary: replace an order
                  requestBody:
                    content:
                      application/json:
                        example: {"item": "book"}
                  responses:
                    "200": { description: replaced }
            """;

    /** One server, no security, every value given: nothing to fill in. */
    private static final String COMPLETE_SPEC = """
            openapi: 3.1.0
            info:
              title: Health
            servers:
              - url: http://localhost:8080
                description: local
            paths:
              /health:
                get:
                  summary: health check
                  responses:
                    "200": { description: up }
            """;

    @TempDir
    private Path temp;

    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();
    private CommandLine underTest;

    @BeforeEach
    void setUp() throws IOException {
        underTest = new CommandLine(new ImportCommand(temp));
        underTest.setOut(new PrintWriter(out));
        underTest.setErr(new PrintWriter(err));
        Files.writeString(temp.resolve("orders-openapi.yaml"), SPEC, StandardCharsets.UTF_8);
        Files.writeString(temp.resolve("health.yaml"), COMPLETE_SPEC, StandardCharsets.UTF_8);
    }

    @Test
    void call_writesTheScaffoldAndPrintsWhatItWroteAndWhatToDoNext() {
        // when
        var exitCode = underTest.execute("orders-openapi.yaml", "--out", "orders-api");

        // then
        assertThat(exitCode).isZero();
        assertThat(err.toString()).isEmpty();
        assertThat(out.toString().lines().toList())
                .containsExactly(
                        "brat import orders-openapi.yaml → orders-api",
                        "  wrote orders-api/orders-api.brat.yaml   (2 requests in 1 sub-suite, 1 setup request)",
                        "  wrote orders-api/local/env.yaml",
                        "  wrote orders-api/staging/env.yaml",
                        "next: fill in orders-api.brat.yaml, local/env.yaml, set BRAT_SECRET_CLIENT_SECRET, then",
                        "  brat run orders-api -e local");
        assertThat(temp.resolve("orders-api/orders-api.brat.yaml")).isRegularFile();
        assertThat(temp.resolve("orders-api/staging/env.yaml")).isRegularFile();
    }

    @Test
    void call_writesIntoTheDirectoryTheImportSuggestsWithoutOut() {
        // when
        var exitCode = underTest.execute("orders-openapi.yaml");

        // then
        assertThat(exitCode).isZero();
        assertThat(temp.resolve("orders-api/orders-api.brat.yaml")).isRegularFile();
        assertThat(out.toString()).startsWith("brat import orders-openapi.yaml → orders-api\n");
    }

    @Test
    void call_printsOnlyTheRunLineWhenThereIsNothingToFillIn() {
        // when
        var exitCode = underTest.execute("health.yaml", "--out", "health");

        // then
        assertThat(exitCode).isZero();
        assertThat(out.toString().lines().toList())
                .containsExactly(
                        "brat import health.yaml → health",
                        "  wrote health/health.brat.yaml   (1 request)",
                        "  wrote health/local/env.yaml",
                        "next: brat run health -e local");
    }

    @Test
    void call_printsTheBodyFilesAndHonoursBodyFileLines() {
        // when
        var exitCode = underTest.execute("orders-openapi.yaml", "--out", "orders-api", "--body-file-lines", "0");

        // then
        assertThat(exitCode).isZero();
        assertThat(out.toString()).contains("  wrote orders-api/bodies/replace-an-order.json\n");
        assertThat(temp.resolve("orders-api/bodies/replace-an-order.json")).isRegularFile();
    }

    @Test
    void call_exitsWith2ForANegativeBodyFileLines() {
        // when
        var exitCode = underTest.execute("orders-openapi.yaml", "--out", "orders-api", "--body-file-lines", "-1");

        // then
        assertThat(exitCode).isEqualTo(2);
        assertThat(err.toString()).startsWith("brat: ").contains("-1");
        assertThat(out.toString()).isEmpty();
        assertThat(temp.resolve("orders-api")).doesNotExist();
    }

    @Test
    void call_exitsWith2WithoutASpec() {
        // when
        var exitCode = underTest.execute();

        // then
        assertThat(exitCode).isEqualTo(2);
        assertThat(out.toString()).isEmpty();
    }

    @Test
    void call_exitsWith2NamingASpecThatCannotBeRead() {
        // when
        var exitCode = underTest.execute("missing.yaml");

        // then
        assertThat(exitCode).isEqualTo(2);
        assertThat(err.toString()).startsWith("brat: ").contains("missing.yaml");
        assertThat(out.toString()).isEmpty();
    }

    @Test
    void call_exitsWith2SayingASpecIsNotUtf8() throws IOException {
        // given
        Files.writeString(
                temp.resolve("latin1.yaml"),
                SPEC.replace("Orders API", "Bestellungen für Müller"),
                StandardCharsets.ISO_8859_1);

        // when
        var exitCode = underTest.execute("latin1.yaml", "--out", "latin1");

        // then
        assertThat(exitCode).isEqualTo(2);
        assertThat(err.toString()).startsWith("brat: ").contains("latin1.yaml", "UTF-8");
        assertThat(out.toString()).isEmpty();
    }

    @Test
    void call_exitsWith2ForASpecTheImportRefuses() throws IOException {
        // given
        Files.writeString(temp.resolve("swagger.yaml"), "swagger: \"2.0\"\n");

        // when
        var exitCode = underTest.execute("swagger.yaml", "--out", "swagger");

        // then
        assertThat(exitCode).isEqualTo(2);
        assertThat(err.toString()).startsWith("brat: ").contains("Swagger 2");
        assertThat(out.toString()).isEmpty();
        assertThat(temp.resolve("swagger")).doesNotExist();
    }

    @Test
    void call_exitsWith2ForADirectoryThatHoldsFilesAndLeavesThemAlone() throws IOException {
        // given
        var existing = Files.createDirectories(temp.resolve("orders-api"));
        Files.writeString(existing.resolve("mine.brat.yaml"), "name: mine\n");

        // when
        var exitCode = underTest.execute("orders-openapi.yaml", "--out", "orders-api");

        // then
        assertThat(exitCode).isEqualTo(2);
        assertThat(err.toString()).startsWith("brat: ").contains("mine.brat.yaml");
        assertThat(out.toString()).isEmpty();
        assertThat(Files.readString(existing.resolve("mine.brat.yaml"))).isEqualTo("name: mine\n");
        try (var files = Files.list(existing)) {
            assertThat(files.toList()).hasSize(1);
        }
    }

    @Test
    void call_printsItsUsageToStdoutForHelp() {
        // when
        var exitCode = underTest.execute("--help");

        // then
        assertThat(exitCode).isZero();
        assertThat(out.toString()).contains("Usage: import", "--out", "--body-file-lines");
    }

    @Test
    void call_isListedInTheUsageOfBrat() {
        // given
        var brat = new CommandLine(new BratCli());
        var bratOut = new StringWriter();
        brat.setOut(new PrintWriter(bratOut));

        // when
        brat.execute("--help");

        // then
        assertThat(bratOut.toString()).contains("import", "Imports an OpenAPI 3 spec", "brat import");
    }
}
