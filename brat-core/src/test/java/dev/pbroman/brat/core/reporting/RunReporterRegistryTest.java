package dev.pbroman.brat.core.reporting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import dev.pbroman.brat.core.api.listener.RunListener;
import dev.pbroman.brat.core.api.reporting.ReporterContext;
import dev.pbroman.brat.core.api.reporting.RunReporter;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RunReporterRegistryTest {

    private static RunReporter reporter(String name) {
        return new RunReporter() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public RunListener create(ReporterContext context) {
                return event -> {};
            }
        };
    }

    @Test
    void get_returnsTheReporterRegisteredUnderTheName() {
        // given
        var console = reporter("console");
        var underTest = new RunReporterRegistry(List.of(reporter("junit"), console));

        // when
        var found = underTest.get("console");

        // then
        assertThat(found).isSameAs(console);
    }

    @Test
    void constructor_letsALaterReporterReplaceAnEarlierOneOfTheSameName() {
        // given
        var later = reporter("console");
        var underTest = new RunReporterRegistry(List.of(reporter("console"), later));

        // when
        var found = underTest.get("console");

        // then
        assertThat(found).isSameAs(later);
    }

    @Test
    void get_throwsForAnUnknownNameNamingTheRegisteredOnes() {
        // given
        var underTest = new RunReporterRegistry(List.of(reporter("console"), reporter("junit")));

        // when / then
        assertThatThrownBy(() -> underTest.get("consle"))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("consle")
                .hasMessageContaining("console")
                .hasMessageContaining("junit");
    }

    @Test
    void get_matchesTheNameExactly() {
        // given
        var underTest = new RunReporterRegistry(List.of(reporter("console")));

        // when / then
        assertThatThrownBy(() -> underTest.get("Console")).isInstanceOf(BratException.class);
    }

    @Test
    void get_throwsForANullName() {
        // given
        var underTest = new RunReporterRegistry(List.of(reporter("console")));

        // when / then
        assertThatThrownBy(() -> underTest.get(null)).isInstanceOf(BratException.class);
    }

    @Test
    void get_throwsOnAnEmptyRegistry() {
        // given
        var underTest = new RunReporterRegistry(List.of());

        // when / then
        assertThatThrownBy(() -> underTest.get("console")).isInstanceOf(BratException.class);
    }

    @Test
    void constructor_throwsForANullCollection() {
        // when / then
        assertThatThrownBy(() -> new RunReporterRegistry(null)).isInstanceOf(BratException.class);
    }

    @Test
    void constructor_throwsForANullElement() {
        // when / then
        assertThatThrownBy(() -> new RunReporterRegistry(Arrays.asList(reporter("console"), null)))
                .isInstanceOf(BratException.class);
    }

    @Test
    void constructor_throwsForANullOrBlankName() {
        // when / then
        assertThatThrownBy(() -> new RunReporterRegistry(List.of(reporter(null))))
                .isInstanceOf(BratException.class);
        assertThatThrownBy(() -> new RunReporterRegistry(List.of(reporter(" "))))
                .isInstanceOf(BratException.class);
    }

    @Test
    void constructor_doesNotRetainTheCollection() {
        // given
        var reporters = new ArrayList<>(List.of(reporter("console")));
        var underTest = new RunReporterRegistry(reporters);

        // when
        reporters.add(reporter("junit"));

        // then
        assertThatThrownBy(() -> underTest.get("junit")).isInstanceOf(BratException.class);
    }
}
