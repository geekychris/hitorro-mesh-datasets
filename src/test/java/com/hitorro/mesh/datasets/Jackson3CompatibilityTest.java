package com.hitorro.mesh.datasets;

import com.hitorro.mesh.datasets.envelope.RecordEnvelope;
import com.hitorro.mesh.datasets.loader.JoinableWinsLoader;
import com.hitorro.mesh.datasets.loader.ManifestLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins hitorro-mesh-datasets' Jackson behaviour to 3.x (Jackson 2.21).
 *
 * <p>{@code jackson2-golden.txt} was produced by the 3.1.0 sources of this repo on Jackson
 * 2.21.8: every bundled manifest and the joinable wins as loaded, and a RecordEnvelope, each
 * written as JSON with a Jackson 2 default mapper (generator:
 * {@code ~/hitorro-j3/tools/Jackson2MeshDatasetsGolden.java.txt}).
 */
class Jackson3CompatibilityTest {

    private static final ObjectMapper OUT = JsonMapper.builderWithJackson2Defaults().build();
    private static Map<String, String> golden;

    @TempDir
    Path dir;

    @BeforeAll
    static void load() throws Exception {
        golden = new LinkedHashMap<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                Jackson3CompatibilityTest.class.getResourceAsStream("jackson2-golden.txt"), StandardCharsets.UTF_8))) {
            r.lines().forEach(l -> {
                int i = l.indexOf('=');
                golden.put(l.substring(0, i), l.substring(i + 1));
            });
        }
    }

    @Test
    @DisplayName("every bundled manifest loads to the same object as on Jackson 2")
    void manifests() throws Exception {
        List<String> ids = ManifestLoader.listBundled();
        assertThat(ids).isNotEmpty();
        for (String id : ids) {
            assertThat(OUT.writeValueAsString(ManifestLoader.loadBundled(id))).as(id)
                    .isEqualTo(golden.get("manifest." + id));
        }
        assertThat(golden.keySet().stream().filter(k -> k.startsWith("manifest.")).count()).isEqualTo(ids.size());
    }

    @Test
    @DisplayName("joinable wins load the same")
    void joinableWins() {
        assertThat(OUT.writeValueAsString(JoinableWinsLoader.loadBundled())).isEqualTo(golden.get("joinableWins"));
    }

    @Test
    @DisplayName("RecordEnvelope writes @-prefixed fields in declaration order, NON_NULL, and reads back")
    void envelope() {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("en", "San Francisco");
        names.put("zh", "旧金山");
        RecordEnvelope env = new RecordEnvelope("place:us/ca/sf", List.of("geo.Place", "geo.City"), names,
                OUT.readTree("{\"type\":\"Point\",\"coordinates\":[-122.42,37.77]}"), null,
                new RecordEnvelope.SourceRef("geonames", "1.0", "5391959"), null);
        assertThat(OUT.writeValueAsString(env)).isEqualTo(golden.get("envelope"));
        RecordEnvelope back = OUT.readValue(golden.get("envelope"), RecordEnvelope.class);
        assertThat(OUT.writeValueAsString(back)).isEqualTo(golden.get("envelopeRead"));
    }

    @Test
    @DisplayName("a missing or malformed manifest is still an IOException (Jackson 3 exceptions are unchecked)")
    void badManifest() throws Exception {
        assertThatThrownBy(() -> ManifestLoader.loadBundled("nope")).isInstanceOf(IOException.class);
        Path bad = dir.resolve("bad.yaml");
        Files.writeString(bad, "id: [unclosed\n");
        assertThatThrownBy(() -> ManifestLoader.loadFrom(bad)).isInstanceOf(IOException.class);
        Path badJson = dir.resolve("bad.json");
        Files.writeString(badJson, "{\"id\": ");
        assertThatThrownBy(() -> ManifestLoader.loadFrom(badJson)).isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("unknown manifest properties are ignored (YAML and JSON)")
    void unknownProperties() throws Exception {
        Path y = dir.resolve("m.yaml");
        Files.writeString(y, "id: x\ntitle: T\nnotAField: 1\n");
        assertThat(ManifestLoader.loadFrom(y).id()).isEqualTo("x");
        Path j = dir.resolve("m.json");
        Files.writeString(j, "{\"id\":\"x\",\"title\":\"T\",\"notAField\":1}");
        assertThat(ManifestLoader.loadFrom(j).id()).isEqualTo("x");
    }
}
