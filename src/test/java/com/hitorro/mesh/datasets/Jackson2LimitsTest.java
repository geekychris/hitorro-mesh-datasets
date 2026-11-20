package com.hitorro.mesh.datasets;

import com.hitorro.mesh.datasets.loader.ManifestLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the mappers' stream limits to what 3.x (Jackson 2.21.8) accepted and rejected, as
 * measured: nesting 1000 accepted and 1001 rejected (Jackson 3 default: 500), JSON strings of
 * 20,000,000 chars accepted and 20,000,001 rejected (Jackson 3 default: 100,000,000). YAML
 * documents over ~3 MB are rejected by snakeyaml's code point limit in both versions.
 */
class Jackson2LimitsTest {

    @TempDir
    Path dir;

    private static String nested(int n) {
        return "[".repeat(n) + "]".repeat(n);
    }

    @Test
    @DisplayName("JSON and YAML mappers: nesting 1000 accepted, 1001 rejected")
    void nesting() {
        for (ObjectMapper m : new ObjectMapper[]{Jackson2Defaults.json().build(), Jackson2Defaults.yaml().build()}) {
            assertThatCode(() -> m.readTree(nested(501))).doesNotThrowAnyException();
            assertThatCode(() -> m.readTree(nested(1000))).doesNotThrowAnyException();
            assertThatThrownBy(() -> m.readTree(nested(1001))).isInstanceOf(JacksonException.class);
        }
    }

    @Test
    @DisplayName("JSON strings: 20,000,000 chars accepted, 20,000,001 rejected")
    void stringLength() {
        ObjectMapper json = Jackson2Defaults.json().build();
        String ok = "x".repeat(20_000_000);
        assertThatCode(() -> json.readTree("\"" + ok + "\"")).doesNotThrowAnyException();
        assertThatThrownBy(() -> json.readTree("\"" + ok + "x\"")).isInstanceOf(JacksonException.class);
    }

    @Test
    @DisplayName("ManifestLoader: a manifest with an ignored 600-deep field loads; 1001 deep is an IOException")
    void manifestNesting() throws Exception {
        Path ok = dir.resolve("deep.json");
        Files.writeString(ok, "{\"id\":\"x\",\"title\":\"T\",\"extra\":" + nested(600) + "}");
        assertThat(ManifestLoader.loadFrom(ok).id()).isEqualTo("x");
        Path okYaml = dir.resolve("deep.yaml");
        Files.writeString(okYaml, "id: x\ntitle: T\nextra: " + nested(600) + "\n");
        assertThat(ManifestLoader.loadFrom(okYaml).id()).isEqualTo("x");
        Path tooDeep = dir.resolve("toodeep.json");
        Files.writeString(tooDeep, "{\"id\":\"x\",\"extra\":" + nested(1001) + "}");
        assertThatThrownBy(() -> ManifestLoader.loadFrom(tooDeep)).isInstanceOf(IOException.class);
    }
}
