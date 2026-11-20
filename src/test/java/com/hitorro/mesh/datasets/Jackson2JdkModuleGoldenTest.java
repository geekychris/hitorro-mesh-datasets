package com.hitorro.mesh.datasets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.*; import java.net.*; import java.nio.charset.*; import java.nio.file.*; import java.time.*; import java.util.*; import java.util.regex.*; import java.util.concurrent.atomic.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins how this library's JSON and YAML mappers (copies of hitorro-core's) write and read JDK types to Jackson 2.18.12.
 *
 * <p>The {@code jackson2-jdk-*.txt} files were produced by running exactly these samples through
 * Jackson 2.18.12 ({@code new ObjectMapper()}, {@code new ObjectMapper(new SmileFactory())},
 * {@code new YAMLMapper()}) with the jsr310 and jdk8 modules registered. Without the modules,
 * 2.18 could not write or read java.time and Optional at all; Jackson 3 has them built in and
 * matches the modules. Generators: {@code ~/hitorro-j3/tools/Jackson2JdkTypesGolden*.java.txt}.
 * Every write ({@code .value}, {@code .tree}, {@code .key}) and every read of 2.18-written data
 * ({@code .read}, {@code .readKey}, and Locale strings in other forms) must match line for line.
 * {@link Jackson2JdkModule} makes Locale, Month and {@code byte[]} trees match; the rest already did.
 */
class Jackson2JdkModuleGoldenTest {

  public enum Color { RED, GREEN; @Override public String toString() { return "c-" + name().toLowerCase(); } }
  public static class OptBean { public Optional<String> o = Optional.of("x"); public Optional<String> e = Optional.empty(); public OptionalInt oi = OptionalInt.of(3); }
  public static class LocBean { public Locale loc = Locale.CANADA_FRENCH; public Map<Locale,Integer> byLoc = new LinkedHashMap<>(Map.of(Locale.US, 1)); }
  record S(String name, Object value, Class<?> type, boolean key) {}
  static List<S> samples() throws Exception {
    List<S> l = new ArrayList<>();
    Object[][] locs = {{"en", new Locale("en")}, {"en_US", Locale.US}, {"fr_CA", Locale.CANADA_FRENCH}, {"zh_Hant_TW", Locale.forLanguageTag("zh-Hant-TW")},
      {"sr_Latn", Locale.forLanguageTag("sr-Latn")}, {"en_US_WIN", new Locale("en","US","WIN")}, {"en_US_POSIX", new Locale("en","US","POSIX")}, {"no_NO_NY", new Locale("no","NO","NY")},
      {"ja_JP_JP", new Locale("ja","JP","JP")}, {"th_TH_TH", new Locale("th","TH","TH")}, {"th-u-nu", Locale.forLanguageTag("th-TH-u-nu-thai")}, {"de-u-co", Locale.forLanguageTag("de-DE-u-co-phonebk")},
      {"x-privateuse", Locale.forLanguageTag("en-x-foo")}, {"root", Locale.ROOT}, {"_US", new Locale("","US")}, {"es_419", new Locale("es","419")}, {"iw", new Locale("iw")}, {"he", new Locale("he")},
      {"zh_CN", Locale.SIMPLIFIED_CHINESE}, {"und", Locale.forLanguageTag("und")}};
    for (Object[] o : locs) l.add(new S("Locale." + o[0], o[1], Locale.class, true));
    l.add(new S("LocBean", new LocBean(), LocBean.class, false));
    l.add(new S("Currency", Currency.getInstance("USD"), Currency.class, true));
    l.add(new S("TimeZone.NY", TimeZone.getTimeZone("America/New_York"), TimeZone.class, false));
    l.add(new S("TimeZone.GMT+0530", TimeZone.getTimeZone("GMT+05:30"), TimeZone.class, false));
    l.add(new S("ZoneId", ZoneId.of("Europe/Paris"), ZoneId.class, true));
    l.add(new S("ZoneOffset", ZoneOffset.ofHours(2), ZoneOffset.class, true));
    l.add(new S("URL", new URL("https://a.b/c?d=e#f"), URL.class, true));
    l.add(new S("URI", URI.create("urn:isbn:1 2".replace(" ", "%20")), URI.class, true));
    l.add(new S("File", new File("/tmp/x y"), File.class, true));
    l.add(new S("Path", Path.of("/tmp/x"), Path.class, false));
    l.add(new S("InetAddress.v4", InetAddress.getByName("127.0.0.1"), InetAddress.class, false));
    l.add(new S("InetAddress.v6", InetAddress.getByName("::1"), InetAddress.class, false));
    l.add(new S("InetSocketAddress", new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 8080), InetSocketAddress.class, false));
    l.add(new S("UUID", UUID.fromString("123e4567-e89b-12d3-a456-426614174000"), UUID.class, true));
    l.add(new S("Class", String.class, Class.class, true));
    l.add(new S("Class.int", int.class, Class.class, false));
    l.add(new S("Class.array", String[].class, Class.class, false));
    l.add(new S("Charset", StandardCharsets.UTF_8, Charset.class, false));
    l.add(new S("Pattern", Pattern.compile("a+b"), Pattern.class, false));
    l.add(new S("bytes", new byte[]{1,2,3}, byte[].class, false));
    l.add(new S("chars", new char[]{'a','b'}, char[].class, false));
    l.add(new S("enum.toString", Color.GREEN, Color.class, true));
    l.add(new S("StringBuilder", new StringBuilder("sb"), StringBuilder.class, false));
    l.add(new S("AtomicInteger", new AtomicInteger(5), AtomicInteger.class, false));
    l.add(new S("AtomicBoolean", new AtomicBoolean(true), AtomicBoolean.class, false));
    l.add(new S("AtomicReference", new AtomicReference<>("r"), AtomicReference.class, false));
    l.add(new S("BigDecimal.1E+3", new java.math.BigDecimal("1E+3"), java.math.BigDecimal.class, false));
    l.add(new S("Date", new Date(1700000000123L), Date.class, true));
    Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC")); c.setTimeInMillis(1700000000123L);
    l.add(new S("Calendar", c, Calendar.class, false));
    l.add(new S("sql.Date", new java.sql.Date(1700000000123L), java.sql.Date.class, false));
    l.add(new S("sql.Timestamp", new java.sql.Timestamp(1700000000123L), java.sql.Timestamp.class, false));
    Instant i = Instant.ofEpochMilli(1700000000123L);
    l.add(new S("Instant", i, Instant.class, true));
    l.add(new S("LocalDate", LocalDate.of(2026, 11, 20), LocalDate.class, true));
    l.add(new S("LocalDateTime", LocalDateTime.of(2026, 11, 20, 13, 45, 6, 789000000), LocalDateTime.class, true));
    l.add(new S("LocalTime", LocalTime.of(13, 45, 6), LocalTime.class, true));
    l.add(new S("OffsetDateTime", OffsetDateTime.of(2026, 11, 20, 13, 45, 6, 0, ZoneOffset.ofHours(2)), OffsetDateTime.class, true));
    l.add(new S("ZonedDateTime", ZonedDateTime.of(2026, 11, 20, 13, 45, 6, 0, ZoneId.of("Europe/Paris")), ZonedDateTime.class, true));
    l.add(new S("Duration", Duration.ofSeconds(90, 5), Duration.class, true));
    l.add(new S("Period", Period.of(1, 2, 3), Period.class, true));
    l.add(new S("Year", Year.of(2026), Year.class, true));
    l.add(new S("YearMonth", YearMonth.of(2026, 11), YearMonth.class, true));
    l.add(new S("MonthDay", MonthDay.of(11, 20), MonthDay.class, true));
    l.add(new S("DayOfWeek", DayOfWeek.FRIDAY, DayOfWeek.class, true));
    l.add(new S("Month", Month.NOVEMBER, Month.class, true));
    l.add(new S("Optional", Optional.of("x"), Optional.class, false));
    l.add(new S("Optional.empty", Optional.empty(), Optional.class, false));
    l.add(new S("OptionalInt", OptionalInt.of(3), OptionalInt.class, false));
    l.add(new S("OptBean", new OptBean(), OptBean.class, false));
    return l;
  }
  static String t(java.util.concurrent.Callable<Object> c) { try { return String.valueOf(c.call()); } catch (Throwable e) { return "!" + e.getClass().getSimpleName(); } }
  static String show(Object o) {
    if (o == null) return "null"; if (o instanceof byte[] b) return "bytes" + Arrays.toString(b); if (o instanceof char[] ch) return "chars" + Arrays.toString(ch);
    if (o instanceof Pattern p) return "Pattern:" + p.pattern(); if (o instanceof Calendar cal) return "Calendar:" + cal.getTimeInMillis() + "@" + cal.getTimeZone().getID();
    if (o instanceof Locale lc) return "Locale[" + lc.toLanguageTag() + "|" + lc + "]"; if (o instanceof TimeZone tz) return "TimeZone:" + tz.getID();
    if (o instanceof LocBean b) return "LocBean{" + show(b.loc) + "," + b.byLoc.keySet().stream().map(Jackson2JdkModuleGoldenTest::show).toList() + "}";
    if (o instanceof OptBean b) return "OptBean{" + b.o + "," + b.e + "," + b.oi + "}";
    return o.getClass().getSimpleName() + ":" + o;
  }

  interface Codec { String write(ObjectMapper om, Object v) throws Exception; Object read(ObjectMapper om, String s, JavaType t) throws Exception; }

  static final Codec JSON = new Codec() {
    public String write(ObjectMapper om, Object v) { return om.writeValueAsString(v); }
    public Object read(ObjectMapper om, String s, JavaType t) { return om.readValue(s, t); }
  };
  static final Codec YAML = new Codec() {
    public String write(ObjectMapper om, Object v) { return om.writeValueAsString(v).replace("\n", "\\n"); }
    public Object read(ObjectMapper om, String s, JavaType t) { return om.readValue(s.replace("\\n", "\n"), t); }
  };
  static final Codec SMILE = new Codec() {
    public String write(ObjectMapper om, Object v) { return HexFormat.of().formatHex(om.writeValueAsBytes(v)); }
    public Object read(ObjectMapper om, String s, JavaType t) { return om.readValue(HexFormat.of().parseHex(s), t); }
  };

  static Map<String, String> golden(String name) throws IOException {
    Map<String, String> m = new LinkedHashMap<>();
    try (BufferedReader r = new BufferedReader(new InputStreamReader(
        Jackson2JdkModuleGoldenTest.class.getResourceAsStream(name), StandardCharsets.UTF_8))) {
      r.lines().forEach(l -> { int i = l.indexOf('='); m.put(l.substring(0, i), l.substring(i + 1)); });
    }
    return m;
  }

  static void check(ObjectMapper om, Codec c, String format, boolean tagInputs) throws Exception {
    Map<String, String> write = new LinkedHashMap<>();
    for (S s : samples()) {
      write.put(s.name + ".value", t(() -> c.write(om, s.value)));
      write.put(s.name + ".tree", t(() -> { JsonNode n = om.valueToTree(s.value); return n.getNodeType() + ":" + n; }));
      if (s.key) write.put(s.name + ".key", t(() -> c.write(om, Collections.singletonMap(s.value, 1))));
    }
    Map<String, String> goldenWrite = golden("jackson2-jdk-" + format + "-write.txt");
    assertThat(write).containsExactlyEntriesOf(goldenWrite);

    Map<String, String> read = new LinkedHashMap<>();
    for (S s : samples()) {
      String v = goldenWrite.get(s.name + ".value");
      if (v != null && !v.startsWith("!")) read.put(s.name + ".read", t(() -> show(c.read(om, v, om.getTypeFactory().constructType(s.type)))));
      String k = goldenWrite.get(s.name + ".key");
      if (s.key && k != null && !k.startsWith("!")) read.put(s.name + ".readKey", t(() -> {
        Map<?, ?> m = (Map<?, ?>) c.read(om, k, om.getTypeFactory().constructMapType(LinkedHashMap.class, s.type, Integer.class));
        return show(m.keySet().iterator().next());
      }));
    }
    if (tagInputs) {
      for (String tag : new String[]{"\"fr-CA\"", "\"zh-Hant-TW\"", "\"en-US-POSIX\"", "\"und\"", "\"\"", "\"en_US\"", "\"EN_us\"", "\"en_US_WIN\"", "\"ja_JP_JP_#u-ca-japanese\"", "\"th_TH_TH_#u-nu-thai\"", "\"sr__#Latn\"", "\"zh_TW_#Hant\"", "\"de__POSIX\"", "\"_US\"", "\"x-y\""}) {
        String doc = format.equals("yaml") ? "--- " + tag + "\\n" : tag;
        read.put("Locale.readInput[" + tag + "]", t(() -> show(c.read(om, doc, om.getTypeFactory().constructType(Locale.class)))));
      }
    }
    assertThat(read).containsExactlyEntriesOf(golden("jackson2-jdk-" + format + "-read.txt"));
  }

  @Test
  @DisplayName("JSON: JDK types written and read as Jackson 2.18 did (Locale, Month, byte[] trees fixed)")
  void json() throws Exception {
    check(Jackson2Defaults.json().build(), JSON, "json", true);
  }

  @Test
  @DisplayName("YAML: JDK types written and read as Jackson 2.18 did")
  void yaml() throws Exception {
    check(YamlMappers.yaml(), YAML, "yaml", true);
  }

  @Test
  @DisplayName("Locale examples, spelled out")
  void localeExamples() {
    ObjectMapper om = Jackson2Defaults.json().build();
    assertThat(om.writeValueAsString(Locale.CANADA_FRENCH)).isEqualTo("\"fr_CA\"");
    assertThat(om.writeValueAsString(Locale.forLanguageTag("zh-Hant-TW"))).isEqualTo("\"zh_TW_#Hant\"");
    assertThat(om.writeValueAsString(Locale.ROOT)).isEqualTo("\"\"");
    assertThat(om.readValue("\"zh_TW_#Hant\"", Locale.class)).isEqualTo(Locale.forLanguageTag("zh-Hant-TW"));
    assertThat(om.readValue("\" fr_CA \"", Locale.class)).isEqualTo(Locale.CANADA_FRENCH);
    assertThat(om.readValue("\"\"", Locale.class)).isEqualTo(Locale.ROOT);
    assertThat(om.writeValueAsString(Month.NOVEMBER)).isEqualTo("\"NOVEMBER\"");
    assertThat(om.valueToTree(new byte[]{1, 2, 3}).isBinary()).isTrue();
    assertThat(om.writeValueAsString(new byte[]{1, 2, 3})).isEqualTo("\"AQID\"");
  }
}
