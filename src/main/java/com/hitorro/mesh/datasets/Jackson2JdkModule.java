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

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.KeyDeserializer;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.deser.std.StdScalarDeserializer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.node.TreeBuildingGenerator;
import tools.jackson.databind.ser.jdk.ByteArraySerializer;
import tools.jackson.databind.ser.std.StdScalarSerializer;

import java.time.Month;
import java.util.IllformedLocaleException;
import java.util.Locale;

/**
 * JDK types that Jackson 3 writes or reads differently from Jackson 2.18, restored to 2.18's
 * behaviour. {@link Jackson2Defaults} and {@link YamlMappers} register it.
 *
 * <p>Measured against 2.18.12 (with the jsr310/jdk8 modules for java.time and Optional, which
 * Jackson 3 has built in):
 * <ul>
 *   <li>{@link Locale} values: 2.18 wrote {@code Locale.toString()} ({@code "fr_CA"},
 *       {@code "zh_TW_#Hant"}, {@code "_US"}); Jackson 3 writes the language tag ({@code "fr-CA"},
 *       {@code "zh-Hant-TW"}, {@code "und-US"}). Map keys were {@code toString()} in both.</li>
 *   <li>{@link Locale} reading: Jackson 3 drops the script and extensions of {@code "zh_TW_#Hant"},
 *       {@code "sr__#Latn"}, {@code "de_DE_#u-co-phonebk"} (they end up in the variant). The
 *       parser here is 2.18.12's {@code FromStringDeserializer._deserializeLocale}, values trimmed
 *       and {@code ""} giving {@link Locale#ROOT}, map keys untrimmed, as in 2.18.</li>
 *   <li>{@link Month}: 2.18 wrote the enum name ({@code "NOVEMBER"}); Jackson 3 writes a zero-based
 *       number ({@code 10}).</li>
 *   <li>{@code byte[]} in {@code valueToTree}: 2.18 made a binary node; Jackson 3 a POJO node.
 *       Streamed output (JSON Base64, Smile binary) is unchanged.</li>
 * </ul>
 * Currency, TimeZone, ZoneId, URL, URI, File, Path, InetAddress, UUID, Class, Charset, Pattern,
 * char[], enums, atomics, Date/Calendar/java.sql, java.time (timestamps on) and Optional were
 * measured identical and are left alone.
 */
// Copy of hitorro-core com.hitorro.util.json.Jackson2JdkModule (core commit d27ea01); only the package
// and one javadoc link differ. Keep the two identical.
public final class Jackson2JdkModule extends SimpleModule {

    private static final long serialVersionUID = 1L;

    public Jackson2JdkModule() {
        super("HiTorroJackson2Jdk");
        addSerializer(Locale.class, new LocaleSerializer());
        addDeserializer(Locale.class, new LocaleDeserializer());
        addKeyDeserializer(Locale.class, new LocaleKeyDeserializer());
        addSerializer(Month.class, new MonthSerializer());
        addSerializer(byte[].class, new BinaryNodeByteArraySerializer());
    }

    /** Jackson 2's {@code ToStringSerializer} for Locale. */
    static final class LocaleSerializer extends StdScalarSerializer<Locale> {
        LocaleSerializer() {
            super(Locale.class);
        }

        @Override
        public void serialize(Locale value, JsonGenerator g, SerializationContext ctxt) throws JacksonException {
            g.writeString(value.toString());
        }
    }

    /** Jackson 2.18.12's Locale deserializer: trimmed; {@code ""} is {@link Locale#ROOT}. */
    static final class LocaleDeserializer extends StdScalarDeserializer<Locale> {
        LocaleDeserializer() {
            super(Locale.class);
        }

        @Override
        public Locale deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
            if (p.hasToken(JsonToken.VALUE_STRING)) {
                String text = p.getString().trim();
                if (text.isEmpty()) {
                    return Locale.ROOT;
                }
                return parse(text);
            }
            if (p.hasToken(JsonToken.VALUE_EMBEDDED_OBJECT) && p.getEmbeddedObject() instanceof Locale l) {
                return l;
            }
            return (Locale) ctxt.handleUnexpectedToken(Locale.class, p);
        }

        @Override
        public Object getEmptyValue(DeserializationContext ctxt) {
            return Locale.ROOT;
        }
    }

    /** Jackson 2.18's Locale key deserializer: the same parse, key not trimmed. */
    static final class LocaleKeyDeserializer extends KeyDeserializer {
        @Override
        public Object deserializeKey(String key, DeserializationContext ctxt) throws JacksonException {
            try {
                return parse(key);
            } catch (IllegalArgumentException e) {
                return ctxt.handleWeirdKey(Locale.class, key, "not a valid representation: %s", e.getMessage());
            }
        }
    }

    /** {@link Month} as an enum (name), as Jackson 2 wrote it, honouring the enum features. */
    static final class MonthSerializer extends StdScalarSerializer<Month> {
        MonthSerializer() {
            super(Month.class);
        }

        @Override
        public void serialize(Month value, JsonGenerator g, SerializationContext ctxt) throws JacksonException {
            if (ctxt.isEnabled(EnumFeature.WRITE_ENUMS_USING_INDEX)) {
                g.writeNumber(value.ordinal());
            } else if (ctxt.isEnabled(EnumFeature.WRITE_ENUMS_USING_TO_STRING)) {
                g.writeString(value.toString());
            } else {
                g.writeString(value.name());
            }
        }
    }

    /** {@code byte[]}: unchanged when streaming; a binary node (not a POJO node) when building a tree. */
    static final class BinaryNodeByteArraySerializer extends ByteArraySerializer {
        private static final long serialVersionUID = 1L;

        @Override
        public void serialize(byte[] value, JsonGenerator g, SerializationContext ctxt) throws JacksonException {
            if (g instanceof TreeBuildingGenerator) {
                g.writeTree(ctxt.getConfig().getNodeFactory().binaryNode(value));
            } else {
                super.serialize(value, g, ctxt);
            }
        }
    }

    // ------------------------------------------------------------ Jackson 2.18.12 FromStringDeserializer

    private static final String LOCALE_EXT_MARKER = "_#";

    static Locale parse(String value) {
        int ix = firstHyphenOrUnderscore(value);
        if (ix < 0) { // single argument
            return new Locale(value);
        }
        String first = value.substring(0, ix);
        value = value.substring(ix + 1);
        ix = firstHyphenOrUnderscore(value);
        if (ix < 0) { // two pieces
            return new Locale(first, value);
        }
        String second = value.substring(0, ix);
        int extMarkerIx = value.indexOf(LOCALE_EXT_MARKER);
        if (extMarkerIx < 0) {
            return new Locale(first, second, value.substring(ix + 1));
        }
        return bcp47(value, ix, first, second, extMarkerIx);
    }

    private static int firstHyphenOrUnderscore(String str) {
        for (int i = 0, end = str.length(); i < end; ++i) {
            char c = str.charAt(i);
            if (c == '_' || c == '-') {
                return i;
            }
        }
        return -1;
    }

    private static Locale bcp47(String value, int ix, String first, String second, int extMarkerIx) {
        String third = "";
        try {
            if (extMarkerIx > 0 && extMarkerIx > ix) {
                third = value.substring(ix + 1, extMarkerIx);
            }
            value = value.substring(extMarkerIx + 2);

            int underscoreIx = value.indexOf('_');
            if (underscoreIx < 0) {
                int hyphenIx = value.indexOf('-');
                if (hyphenIx < 0) {
                    return new Locale.Builder().setLanguage(first)
                            .setRegion(second)
                            .setVariant(third)
                            .setScript(value)
                            .build();
                }
                return new Locale.Builder().setLanguage(first)
                        .setRegion(second).setVariant(third)
                        .setExtension(value.charAt(0), value.substring(hyphenIx + 1))
                        .build();
            }
            final int len = value.length();
            Locale.Builder b = new Locale.Builder()
                    .setLanguage(first)
                    .setRegion(second).setVariant(third)
                    .setScript(value.substring(0, underscoreIx));
            if ((underscoreIx + 1) < len) {
                b = b.setExtension(value.charAt(underscoreIx + 1),
                        value.substring(Math.min(len, underscoreIx + 3)));
            }
            return b.build();
        } catch (IllformedLocaleException ex) {
            return new Locale(first, second, third);
        }
    }
}
