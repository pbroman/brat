package dev.pbroman.brat.openapi;

import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImportOptionsTest {

    @Test
    void constructor_acceptsZero() {
        // when
        var options = new ImportOptions(0);

        // then
        assertThat(options.bodyFileLines()).isZero();
    }

    @Test
    void constructor_rejectsANegativeNumberNamingIt() {
        // when / then
        assertThatThrownBy(() -> new ImportOptions(-1))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("-1");
    }

    @Test
    void defaults_allowTenLines() {
        // when
        var options = ImportOptions.defaults();

        // then
        assertThat(options.bodyFileLines())
                .isEqualTo(ImportOptions.DEFAULT_BODY_FILE_LINES)
                .isEqualTo(10);
    }
}
