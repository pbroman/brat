package dev.pbroman.brat.openapi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.pbroman.brat.openapi.Scaffold.Note;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScaffoldTest {

    @Test
    void constructor_keepsTheOrderOfTheFiles() {
        // given
        var files = new LinkedHashMap<String, String>();
        files.put("z.brat.yaml", "name: z\n");
        files.put("bodies/a.json", "{}\n");
        files.put("local/env.yaml", "baseUrl: \"\"\n");

        // when
        var scaffold = new Scaffold("z", "z.brat.yaml", files, List.of("local"), 1, 0, 0, List.of());

        // then
        assertThat(scaffold.files())
                .containsExactly(
                        Map.entry("z.brat.yaml", "name: z\n"),
                        Map.entry("bodies/a.json", "{}\n"),
                        Map.entry("local/env.yaml", "baseUrl: \"\"\n"));
    }

    @Test
    void constructor_keepsCopiesThatCannotBeModified() {
        // given
        var files = new LinkedHashMap<String, String>();
        files.put("z.brat.yaml", "name: z\n");
        var environments = new ArrayList<>(List.of("local"));
        var notes = new ArrayList<>(List.of(new Note("local/env.yaml", "fill in baseUrl")));

        // when
        var scaffold = new Scaffold("z", "z.brat.yaml", files, environments, 1, 0, 0, notes);
        files.clear();
        environments.clear();
        notes.clear();

        // then
        assertThat(scaffold.files()).hasSize(1);
        assertThat(scaffold.environments()).containsExactly("local");
        assertThat(scaffold.notes()).hasSize(1);
        assertThatThrownBy(() -> scaffold.files().put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> scaffold.notes().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
