/*
 * Copyright (c) 2006-2025 Chris Collins
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

import tools.jackson.databind.*; import tools.jackson.databind.node.*;
import java.io.*; import java.math.*; import java.nio.file.*; import java.util.*;
/**
 * Pins {@link YamlMappers} YAML writing to Jackson 2.21.8's {@code new YAMLMapper()}: strings that
 * need quoting (YAML 1.1 booleans/nulls/numbers, indicators, comments, colons, quotes, control and
 * non-ASCII characters, long text), numbers, BigInteger/BigDecimal, NaN/Infinity, chars, beans
 * (byte[] and Date), lists, nested trees, empty containers, scalar and null roots, and whether each
 * string reads back unchanged. {@code jackson2-yaml-write.txt} is the output of the same code with
 * Jackson 2.21.8 (generator: ~/hitorro-j3/tools/Jackson2YamlWriteGolden.java.txt).
 * {@code jackson3-yaml-write-lexer.txt} pins the accepted difference: U+2028 and U+2029 are written
 * raw where 2.x wrote the escapes L and P (YAML 1.2 does not treat them as line breaks).
 */
class YamlMappersWriteGoldenTest {
  static final Map<String, String> out = new LinkedHashMap<>();
  static void p(String k, Object v){ out.put(k, String.valueOf(v).replace("\\","\\\\").replace("\n","\\n").replace("\r","\\r")); }
  static boolean jackson(Throwable t){ return t.getClass().getName().startsWith("com.fasterxml.jackson") || t.getClass().getName().startsWith("tools.jackson"); }
  static String tryIt(java.util.concurrent.Callable<Object> c){ try { return String.valueOf(c.call()); } catch(Throwable t){ return "!"+(jackson(t) ? "jackson" : t.getClass().getSimpleName()); } }
  public static class Bean { public String name = "n"; public int count = 3; public long big = 12345678901L; public double d = 1.10; public Boolean flag = true; public String nul;
    public List<String> tags = Arrays.asList("a", "b c"); public Map<String, Object> meta = new LinkedHashMap<>(Map.of("k", 1)); public byte[] bytes = {1, 2, (byte) 255}; public Date when = new Date(1700000000123L); }
  public static final String[] STRINGS = {"plain", "yes", "no", "on", "off", "y", "n", "true", "True", "null", "~", "", " ", "  lead", "trail  ", "0x1F", "017", "1_000", "12", "-12", "1.5", ".5", "1e3", ".inf", ".nan",
    "2026-01-02", "1:30", "a: b", "a:b", "- x", "#c", "a #c", "'q'", "\"dq\"", "multi\nline", "tab\tx", "\u00e9", "\u2028", "a\u0085b", "a\u2029b", "\uFEFFbom", "\uD83D\uDE00", "<<", "=", "@at", "`bt", "!tag", "&anc", "*ali", "%pct", "{x}", "[x]", "x,y", "?q", "|", ">", "a'b", "a\"b", "\\back", "\u0001ctl", "very long " + "word ".repeat(30)};
  public static Map<String, String> compute(Path dir) throws Exception {
    out.clear();
    ObjectMapper y = YamlMappers.yaml();
    for (int i = 0; i < STRINGS.length; i++) { final String s = STRINGS[i];
      p("str." + i, tryIt(() -> y.writeValueAsString(Collections.singletonMap("v", s))));
      p("strRoundTrip." + i, tryIt(() -> String.valueOf(y.readValue(y.writeValueAsString(Collections.singletonMap("v", s)), Map.class).get("v")).equals(s))); }
    Object[] values = {null, true, false, 0, -1, 12345678901L, new BigInteger("12345678901234567890"), 1.10, -0.0, 1e30, Double.NaN, Double.POSITIVE_INFINITY, 0.1f, new BigDecimal("1.10"), new BigDecimal("1E+3"), 'c'};
    for (int i = 0; i < values.length; i++) { final Object v = values[i]; p("val." + i, tryIt(() -> y.writeValueAsString(Collections.singletonMap("v", v)))); }
    p("bean", tryIt(() -> y.writeValueAsString(new Bean())));
    p("list", tryIt(() -> y.writeValueAsString(Arrays.asList(1, "two", null, Arrays.asList(), new LinkedHashMap<>(), Arrays.asList("x", Map.of("k", "v"))))));
    p("nested", tryIt(() -> y.writeValueAsString(Jackson2Defaults.json().build().readTree("{\"a\":{\"b\":[1,{\"c\":null,\"d\":[]}],\"e\":{}},\"f\":\"g\",\"big\":12345678901234567890,\"dec\":1.10}"))));
    p("emptyMap", tryIt(() -> y.writeValueAsString(new LinkedHashMap<>())));
    p("emptyList", tryIt(() -> y.writeValueAsString(new ArrayList<>())));
    p("scalarRoot", tryIt(() -> y.writeValueAsString("root")));
    p("nullRoot", tryIt(() -> y.writeValueAsString(null)));
    p("pretty", tryIt(() -> y.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("a", List.of(1, 2)))));
    p("seqOfMaps", tryIt(() -> y.writeValueAsString(List.of(Map.of("a", 1), Map.of("b", List.of(Map.of("c", 2)))))));
    p("unicodeKey", tryIt(() -> y.writeValueAsString(new LinkedHashMap<>(new TreeMap<>(Map.of("cl\u00e9 \u2028", "v", "yes", "k", "1", "n"))))));
    return out;
  }

  static Map<String, String> load(String name) throws IOException {
    Map<String, String> m = new LinkedHashMap<>();
    try (InputStream in = YamlMappersWriteGoldenTest.class.getResourceAsStream(name)) {
      if (in == null) return m;
      for (String line : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
        if (line.isEmpty()) continue;
        int i = line.indexOf('=');
        m.put(line.substring(0, i), line.substring(i + 1));
      }
    }
    return m;
  }

  @org.junit.jupiter.api.Test
  void matchesJackson2Output(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
    Map<String, String> expected = load("jackson2-yaml-write.txt");
    // Deliberate 4.0.0 differences (see the PR): these keys are pinned to the 4.0.0 output.
    Map<String, String> overrides = load("jackson3-yaml-write-lexer.txt");
    org.assertj.core.api.Assertions.assertThat(overrides).isNotEmpty();
    org.assertj.core.api.Assertions.assertThat(expected).containsKeys(overrides.keySet().toArray(new String[0]));
    expected.putAll(overrides);
    Map<String, String> actual = new LinkedHashMap<>();
    compute(dir).forEach((k, v) -> actual.put(k, v.replace("StringNode", "TextNode")));
    org.assertj.core.api.Assertions.assertThat(expected).hasSize(148);
    org.assertj.core.api.Assertions.assertThat(new java.util.TreeMap<>(actual)).isEqualTo(new java.util.TreeMap<>(expected));
  }
}

