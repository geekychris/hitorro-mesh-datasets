/*
 * Copyright (c) 2006-2026 Chris Collins
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.hitorro.mesh.datasets;

import org.junit.jupiter.api.Test;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLFactory;
import tools.jackson.dataformat.yaml.YAMLMapper;
import tools.jackson.dataformat.yaml.YAMLReadFeature;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class YamlMappersTest {

    /**
     * The shared matrix j3-core measured with Jackson 2.21.8 + snakeyaml 2.5 (also pinned by
     * hitorro-mesh-datasets' copy): ~/hitorro-j3/tools/yaml11-scalars-2.21.8.java.txt, YAML =
     * {@code new YAMLMapper()}. Same program, same output, with Jackson 3's exception name
     * mapped back.
     */
    @Test
    void sharedScalarMatrixMatchesJackson2() throws Exception {
        String expected;
        try (InputStream in = getClass().getResourceAsStream("yaml11-scalars-2.21.8.txt")) {
            expected = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        tools.jackson.databind.ObjectMapper y = YamlMappers.yaml();
        String[] vals = {"yes","Yes","YES","no","No","NO","on","On","ON","off","Off","OFF","y","Y","n","N","true","True","TRUE","false","False","FALSE",
          "~","null","Null","NULL","","0x1F","0X1f","0o17","017","0b101","1_000","+12","-12","12","0","-0","1.5","1.5e3","1.5E+3",".5","-.5","+.5","1.","1_000.5",
          ".inf",".Inf",".INF","-.inf","+.inf",".nan",".NaN",".NAN","1:30","190:20:30","2001-12-14","2001-12-14t21:59:43.10-05:00","12e3","1e3","0.","0001","00","9223372036854775808","1e400","abc","'yes'","\"0x1F\"","=","<<"};
        List<String> actual = new ArrayList<>();
        for (String v : vals) {
            String doc = "k: " + v + "\n";
            String r;
            try { JsonNode n = y.readTree(doc).get("k"); r = n == null ? "<absent>" : n.getNodeType() + ":" + n; } catch (Throwable e) { r = "!" + e.getClass().getSimpleName(); }
            actual.add("[" + v + "]=" + r);
            try { Map<?, ?> m = y.readValue(doc, Map.class); Object o = m.get("k"); r = o == null ? "null" : o.getClass().getSimpleName() + ":" + o; } catch (Throwable e) { r = "!" + e.getClass().getSimpleName(); }
            actual.add("map[" + v + "]=" + r);
        }
        for (String doc : new String[]{"yes: 1\n", "on: x\n", "~: 1\n", "0x1F: a\n", "list: [yes, no, ~, 0x10, 1_0]\n", "k: !!str yes\n", "k: !!int '12'\n", "k: &a yes\nj: *a\n"}) {
            String r; try { r = y.readTree(doc).toString(); } catch (Throwable e) { r = "!" + e.getClass().getSimpleName(); }
            actual.add("doc[" + doc.trim() + "]=" + r);
        }
        actual.replaceAll(s -> s.replace("StreamReadException", "JsonParseException"));
        assertThat(expected.split("\n")).hasSize(149);   // 148 entries; one document entry spans two lines
        assertThat(String.join("\n", actual) + "\n").isEqualTo(expected);
    }

    @Test
    void factoryHasJackson2SettingsAndSurvivesRebuild() {
        YAMLFactory f = YamlMappers.factory();
        assertThat(f.isEnabled(YAMLReadFeature.EMPTY_STRING_AS_NULL)).isTrue();
        assertThat(f.streamReadConstraints().getMaxNestingDepth()).isEqualTo(1000);
        assertThat(f.streamWriteConstraints().getMaxNestingDepth()).isEqualTo(1000);
        assertThat(f.streamReadConstraints().getMaxStringLength()).isEqualTo(20_000_000);
        assertThat(f.isEnabled(StreamReadFeature.USE_FAST_DOUBLE_PARSER)).isFalse();
        // rebuild/copy and mapper rebuilds keep the compat parser
        assertThat(f.rebuild().build().getClass()).isEqualTo(f.getClass());
        assertThat(f.copy().getClass()).isEqualTo(f.getClass());
        YAMLMapper m = YamlMappers.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        assertThat(m.tokenStreamFactory().getClass()).isEqualTo(f.getClass());
        YAMLMapper m2 = m.rebuild().build();
        assertThat(m2.tokenStreamFactory().getClass()).isEqualTo(f.getClass());
        assertThat(m2.readTree("a: yes\nb: 0x1F\nc:\n").toString()).isEqualTo("{\"a\":true,\"b\":31,\"c\":null}");
    }

    @Test
    void sharedMapperIsSingletonAndJackson2Defaults() {
        assertThat(YamlMappers.yaml()).isSameAs(YamlMappers.yaml());
        assertThat(YamlMappers.yaml().readTree("on: off\n").toString()).isEqualTo("{\"on\":false}");
        // 2.x defaults: unknown properties fail (mapper-level configureForJackson2)
        assertThat(YamlMappers.yaml().isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isTrue();
    }
}
