package dev.pbroman.brat.junit;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import dev.pbroman.brat.core.api.reporting.ReportDetail;
import dev.pbroman.brat.core.api.reporting.Show;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutputTest {

    @TempDir
    private Path temp;

    private static Properties properties(String... keysAndValues) {
        var properties = new Properties();
        for (var i = 0; i < keysAndValues.length; i += 2) {
            properties.setProperty(keysAndValues[i], keysAndValues[i + 1]);
        }
        return properties;
    }

    private static Output resolve(Map<String, String> shown, Boolean quiet, Properties system, Properties file) {
        return Output.resolve(shown, quiet, system, file);
    }

    @Test
    void resolve_defaultsToBratRunsDetailAndPrinting() {
        // when
        var output = resolve(Map.of(), null, new Properties(), new Properties());

        // then
        assertThat(output.quiet()).isFalse();
        assertThat(output.detail()).isEqualTo(ReportDetail.of(Map.of()));
    }

    @Test
    void resolve_readsADetailFromTheFile() {
        // when
        var output = resolve(Map.of(), null, new Properties(), properties("brat.show.response", "all"));

        // then
        assertThat(output.detail()).isEqualTo(ReportDetail.of(Map.of("response", "all")));
    }

    @Test
    void resolve_letsASystemPropertyWinOverTheFile() {
        // when
        var output = resolve(
                Map.of(),
                null,
                properties("brat.show.response", "none"),
                properties("brat.show.response", "all", "brat.show.request", "all"));

        // then
        assertThat(output.detail()).isEqualTo(ReportDetail.of(Map.of("response", "none", "request", "all")));
    }

    @Test
    void resolve_letsTheTestsOwnChoiceWinOverBoth() {
        // when
        var output = resolve(
                Map.of("response", "failures"),
                null,
                properties("brat.show.response", "none"),
                properties("brat.show.response", "all"));

        // then
        assertThat(output.detail().response()).isEqualTo(Show.FAILURES);
    }

    @Test
    void resolve_readsQuietFromConfigurationInAnyCase() {
        // when
        var fromFile = resolve(Map.of(), null, new Properties(), properties("brat.quiet", "TRUE"));
        var overridden = resolve(Map.of(), null, properties("brat.quiet", "false"), properties("brat.quiet", "true"));

        // then
        assertThat(fromFile.quiet()).isTrue();
        assertThat(overridden.quiet()).isFalse();
    }

    @Test
    void resolve_letsTheTestsQuietWinOverConfiguration() {
        // when
        var output = resolve(Map.of(), Boolean.TRUE, properties("brat.quiet", "false"), new Properties());

        // then
        assertThat(output.quiet()).isTrue();
    }

    @Test
    void resolve_ignoresOtherConfigurationKeys() {
        // when
        var output = resolve(
                Map.of(),
                null,
                properties("brat.other", "x", "junit.jupiter.execution.parallel.enabled", "true"),
                new Properties());

        // then
        assertThat(output).isEqualTo(resolve(Map.of(), null, new Properties(), new Properties()));
    }

    @Test
    void resolve_refusesAShowKeyNamingNoDetailNamingTheDetails() {
        // when / then
        assertThatThrownBy(() -> resolve(Map.of(), null, new Properties(), properties("brat.show.respons", "all")))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("brat.show.respons")
                .hasMessageContaining("response");
    }

    @Test
    void resolve_refusesAQuietThatIsNeitherTrueNorFalse() {
        // when / then
        assertThatThrownBy(() -> resolve(Map.of(), null, properties("brat.quiet", "yes"), new Properties()))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("brat.quiet")
                .hasMessageContaining("yes");
    }

    @Test
    void resolve_refusesAValueTheDetailDoesNotTakeNamingTheKey() {
        // when / then
        assertThatThrownBy(() -> resolve(Map.of(), null, new Properties(), properties("brat.show.assertions", "none")))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("brat.show.assertions");
    }

    @Test
    void junitPlatformProperties_readsTheFileFromTheClassLoader() throws IOException {
        // given
        Files.writeString(temp.resolve("junit-platform.properties"), "brat.show.request=all\n");

        // when
        Properties properties;
        try (var loader = new URLClassLoader(new URL[] {temp.toUri().toURL()}, null)) {
            properties = Output.junitPlatformProperties(loader);
        }

        // then
        assertThat(properties).containsEntry("brat.show.request", "all");
    }

    @Test
    void junitPlatformProperties_isEmptyWithoutAFile() throws IOException {
        // when
        Properties properties;
        try (var loader = new URLClassLoader(new URL[] {temp.toUri().toURL()}, null)) {
            properties = Output.junitPlatformProperties(loader);
        }

        // then
        assertThat(properties).isEmpty();
    }
}
