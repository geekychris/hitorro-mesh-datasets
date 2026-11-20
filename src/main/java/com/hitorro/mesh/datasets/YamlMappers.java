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

import org.snakeyaml.engine.v2.events.ScalarEvent;
import org.snakeyaml.engine.v2.nodes.Tag;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.io.IOContext;
import tools.jackson.core.util.BufferRecycler;
import tools.jackson.core.Base64Variants;
import tools.jackson.dataformat.yaml.YAMLFactory;
import tools.jackson.dataformat.yaml.YAMLFactoryBuilder;
import tools.jackson.dataformat.yaml.YAMLReadFeature;
import tools.jackson.dataformat.yaml.YAMLMapper;
import tools.jackson.dataformat.yaml.YAMLParser;
import org.snakeyaml.engine.v2.api.LoadSettings;

import java.io.InputStream;
import java.io.Reader;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * YAML mappers that read and write exactly what Jackson 2 (2.21, snakeyaml 2.5) did, the YAML
 * counterpart of {@code JsonMappers}. Needs {@code tools.jackson.dataformat:jackson-dataformat-yaml}
 * on the classpath; hitorro-core declares it optional, so add it to a module that uses this class.
 *
 * <p>Jackson 3's {@link YAMLParser} types plain (unquoted) scalars with snakeyaml-engine's YAML 1.2
 * JSON-schema resolver and accepts only {@code true}/{@code false} as booleans. Jackson 2 used
 * snakeyaml's YAML 1.1 resolver: {@code yes/no/on/off} (the case variants snakeyaml lists),
 * {@code 0x1F}, {@code 017}, {@code 0b101}, {@code 1_000}, {@code +12}, {@code ~}, {@code Null},
 * {@code .5} were booleans, numbers and nulls ({@code y/n} stayed strings; {@code .inf/.nan}
 * failed). {@code configureForJackson2()} does not change this. The factory here installs a
 * parser with the YAML 1.1 resolution and Jackson 2's boolean words; number decoding, explicit
 * tags and the rest are Jackson 3's own code (identical to 2.21 apart from YAML 1.2 {@code 0o}
 * octal, kept a string as in 2.x).</p>
 *
 * <p>The factory also gets {@code configureForJackson2()}, the Jackson 2.18 stream limits
 * ({@link Jackson2Defaults#READ_CONSTRAINTS}, {@link Jackson2Defaults#WRITE_CONSTRAINTS})
 * and {@code EMPTY_STRING_AS_NULL} ({@code YAMLFactory.builder()} leaves it off in 3.1.8, so
 * {@code "k:"} would read as {@code ""} instead of null).</p>
 *
 * <p>Known remaining differences, in the YAML 1.2 lexer rather than scalar typing: NEL, U+2028
 * and U+2029 are not line breaks (2.x wrote them as the escapes N, L, P; 4.0.0 writes them raw),
 * and a lone {@code -} in a flow sequence ({@code [-]}) is a syntax error. Writing is otherwise
 * identical to Jackson 2.</p>
 */
// Copy of hitorro-core com.hitorro.util.json.YamlMappers (core commits 5d669de, d27ea01); only the package and
// the constraint constants (Jackson2Defaults instead of JsonMappers) differ. Keep the two identical.
public final class YamlMappers {

    private static volatile YAMLMapper YAML;

    private YamlMappers() { }

    /** A YAML mapper builder with Jackson 2 scalar typing, defaults and stream limits. */
    public static YAMLMapper.Builder builder() {
        return YAMLMapper.builder(factory()).configureForJackson2()
                .addModule(new Jackson2JdkModule());
    }

    /** Shared, immutable YAML mapper ({@code builder().build()}): the 4.0.0 {@code new YAMLMapper()}. */
    public static YAMLMapper yaml() {
        YAMLMapper m = YAML;
        if (m == null) {
            synchronized (YamlMappers.class) {
                if (YAML == null) YAML = builder().build();
                m = YAML;
            }
        }
        return m;
    }

    /** The YAML factory: Jackson 2 factory defaults, stream limits, empty-as-null and scalar typing. */
    public static YAMLFactory factory() {
        return new Jackson2YamlFactory(YAMLFactory.builder().configureForJackson2()
                .enable(YAMLReadFeature.EMPTY_STRING_AS_NULL)
                .streamReadConstraints(Jackson2Defaults.READ_CONSTRAINTS)
                .streamWriteConstraints(Jackson2Defaults.WRITE_CONSTRAINTS));
    }

    /** snakeyaml 2.5 {@code Resolver} implicit resolvers (YAML 1.1), in its order. */
    static final class Yaml11Resolver {
        static final Pattern BOOL = Pattern.compile("^(?:yes|Yes|YES|no|No|NO|true|True|TRUE|false|False|FALSE|on|On|ON|off|Off|OFF)$");
        static final Pattern FLOAT = Pattern.compile("^(" + "[-+]?(?:[0-9][0-9_]*)\\.[0-9_]*(?:[eE][-+]?[0-9]+)?"
                + "|[-+]?(?:[0-9][0-9_]*)(?:[eE][-+]?[0-9]+)"
                + "|[-+]?\\.[0-9_]+(?:[eE][-+]?[0-9]+)?"
                + "|[-+]?[0-9][0-9_]*(?::[0-5]?[0-9])+\\.[0-9_]*"
                + "|[-+]?\\.(?:inf|Inf|INF)" + "|\\.(?:nan|NaN|NAN)" + ")$");
        static final Pattern INT = Pattern.compile("^(?:" + "[-+]?0b_*[0-1][0-1_]*"
                + "|[-+]?0_*[0-7][0-7_]*"
                + "|[-+]?(?:0|[1-9][0-9_]*)"
                + "|[-+]?0x_*[0-9a-fA-F][0-9a-fA-F_]*"
                + "|[-+]?[1-9][0-9_]*(?::[0-5]?[0-9])+"
                + ")$");
        static final Pattern NULL = Pattern.compile("^(?:~|null|Null|NULL| )$");
        static final Tag OTHER = new Tag("tag:yaml.org,2002:timestamp");   // merge/timestamp: Jackson 2 gave a string

        /** {@code Resolver.resolve(NodeId.scalar, value, implicit)}, reduced to the tags Jackson acts on. */
        static Tag resolve(String value, boolean implicit) {
            if (!implicit) return Tag.STR;
            if (value.isEmpty()) return Tag.NULL;                       // EMPTY (null first-char key)
            char c = value.charAt(0);
            int len = value.length();
            if ("yYnNtTfFoO".indexOf(c) >= 0 && len <= 10 && BOOL.matcher(value).matches()) return Tag.BOOL;
            if ("-+0123456789".indexOf(c) >= 0 && len <= 1024 && INT.matcher(value).matches()) return Tag.INT;
            if ("-+0123456789.".indexOf(c) >= 0 && len <= 1024 && FLOAT.matcher(value).matches()) return Tag.FLOAT;
            if (c == '<' && len <= 10 && value.equals("<<")) return OTHER;
            if ("~nN".indexOf(c) >= 0 && len <= 10 && NULL.matcher(value).matches()) return Tag.NULL;
            if ("0123456789".indexOf(c) >= 0 && len <= 50 && TIMESTAMP.matcher(value).matches()) return OTHER;
            // snakeyaml also files NULL under the catch-all key ("\0"), tried for every value last
            if (len <= 10 && NULL.matcher(value).matches()) return Tag.NULL;
            return Tag.STR;
        }
        static final Pattern TIMESTAMP = Pattern.compile(
                "^(?:[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]|[0-9][0-9][0-9][0-9]-[0-9][0-9]?-[0-9][0-9]?(?:[Tt]|[ \t]+)[0-9][0-9]?:[0-9][0-9]:[0-9][0-9](?:\\.[0-9]*)?(?:[ \t]*(?:Z|[-+][0-9][0-9]?(?::[0-9][0-9])?))?)$");
    }

    static final class Jackson2YamlFactory extends YAMLFactory {
        private static final long serialVersionUID = 1L;
        Jackson2YamlFactory(YAMLFactoryBuilder b) { super(b); }
        /** Rebuilding keeps this parser (a plain YAMLFactoryBuilder would build a stock YAMLFactory). */
        @Override public YAMLFactoryBuilder rebuild() {
            return new YAMLFactoryBuilder(this) {
                @Override public YAMLFactory build() { return new Jackson2YamlFactory(this); }
            };
        }
        @Override public YAMLFactory copy() { return rebuild().build(); }
        @Override protected YAMLParser _createParser(ObjectReadContext r, IOContext io, InputStream in) {
            return parser(r, io, _createReader(in, null, io));
        }
        @Override protected YAMLParser _createParser(ObjectReadContext r, IOContext io, Reader rd) {
            return parser(r, io, rd);
        }
        @Override protected YAMLParser _createParser(ObjectReadContext r, IOContext io, char[] data, int offset, int len, boolean recyclable) {
            return parser(r, io, new java.io.CharArrayReader(data, offset, len));
        }
        @Override protected YAMLParser _createParser(ObjectReadContext r, IOContext io, byte[] data, int offset, int len) {
            return parser(r, io, _createReader(data, offset, len, null, io));
        }
        private YAMLParser parser(ObjectReadContext r, IOContext io, Reader rd) {
            return new Jackson2YamlParser(r, io, _getBufferRecycler(), r.getStreamReadFeatures(_streamReadFeatures),
                    r.getFormatReadFeatures(_formatReadFeatures), _loadSettings, rd);
        }
    }

    static final class Jackson2YamlParser extends YAMLParser {
        private static final Pattern OCTAL_12 = Pattern.compile("[-+]?0[oO]");
        Jackson2YamlParser(ObjectReadContext r, IOContext io, BufferRecycler br, int srf, int ff, LoadSettings ls, Reader rd) {
            super(r, io, br, srf, ff, ls, rd);
        }

        /** Jackson 3.1.8's _decodeScalar with the YAML 1.1 resolver and Jackson 2's boolean words. */
        @Override
        protected JsonToken _decodeScalar(ScalarEvent scalar) {
            String value = scalar.getValue();
            _textValue = value;
            _cleanedTextValue = null;
            if (!_cfgEmptyStringsToNull && value.isEmpty()) {
                return JsonToken.VALUE_STRING;
            }
            Optional<String> typeTagOptional = scalar.getTag();
            final int len = value.length();
            if (!typeTagOptional.isPresent() || typeTagOptional.get().equals("!")) {
                // snakeyaml 1.x reported the non-specific tag "!" as implicit; snakeyaml-engine does not
                boolean implicit = typeTagOptional.isPresent() || scalar.getImplicit().canOmitTagInPlainScalar();
                Tag nodeTag = Yaml11Resolver.resolve(value, implicit);
                if (nodeTag == Tag.STR) return JsonToken.VALUE_STRING;
                if (nodeTag == Tag.INT) return _decodeNumberScalar(value, len);
                if (nodeTag == Tag.FLOAT) { _numTypesValid = 0; return cleanYamlFloat(value); }
                if (nodeTag == Tag.BOOL) {
                    Boolean b = _matchYAMLBoolean(value, len);
                    if (b != null) return b ? JsonToken.VALUE_TRUE : JsonToken.VALUE_FALSE;
                } else if (nodeTag == Tag.NULL) {
                    return JsonToken.VALUE_NULL;
                } else {
                    return JsonToken.VALUE_STRING;
                }
                return JsonToken.VALUE_STRING;
            }
            // explicit tag: Jackson 3 and 2 agree, apart from the boolean words handled below
            String typeTag = typeTagOptional.get();
            if (typeTag.startsWith("tag:yaml.org,2002:")) {
                typeTag = typeTag.substring("tag:yaml.org,2002:".length());
                if (typeTag.contains(",")) {
                    final String[] tags = typeTag.split(",");
                    typeTag = (tags.length == 0) ? "" : tags[0];
                }
            }
            if ("binary".equals(typeTag)) {
                value = value.trim();
                try {
                    _binaryValue = Base64Variants.MIME.decode(value);
                } catch (IllegalArgumentException e) {
                    _reportError(e.getMessage());
                }
                return JsonToken.VALUE_EMBEDDED_OBJECT;
            }
            if ("bool".equals(typeTag)) {
                Boolean b = _matchYAMLBoolean(value, len);
                if (b != null) return b ? JsonToken.VALUE_TRUE : JsonToken.VALUE_FALSE;
            } else if (len > 0) {
                if ("int".equals(typeTag)) {
                    // YAML 1.2 "0o" octal is Jackson 3 only; Jackson 2 kept it as a string
                    if (OCTAL_12.matcher(value).lookingAt()) return JsonToken.VALUE_STRING;
                    return _decodeNumberScalar(value, len);
                }
                if ("float".equals(typeTag)) { _numTypesValid = 0; return cleanYamlFloat(value); }
                if ("null".equals(typeTag)) return JsonToken.VALUE_NULL;
            }
            return JsonToken.VALUE_STRING;
        }

        /** Copy of YAMLParser's private _cleanYamlFloat (same in 2.21 and 3.1.8). */
        private JsonToken cleanYamlFloat(String str) {
            final int len = str.length();
            int ix = str.indexOf('_');
            if (ix < 0 || len == 0) {
                _cleanedTextValue = str;
                return JsonToken.VALUE_NUMBER_FLOAT;
            }
            StringBuilder sb = new StringBuilder(len);
            int i = (str.charAt(0) == '+') ? 1 : 0;
            for (; i < len; ++i) {
                char c = str.charAt(i);
                if (c != '_') sb.append(c);
            }
            _cleanedTextValue = sb.toString();
            return JsonToken.VALUE_NUMBER_FLOAT;
        }

        /** Jackson 2.21 with PARSE_BOOLEAN_LIKE_WORDS_AS_STRINGS off (its default). */
        @Override
        protected Boolean _matchYAMLBoolean(String value, int len) {
            switch (len) {
                case 1:
                    switch (value.charAt(0)) {
                        case 'y': case 'Y': return Boolean.TRUE;
                        case 'n': case 'N': return Boolean.FALSE;
                    }
                    break;
                case 2:
                    if ("no".equalsIgnoreCase(value)) return Boolean.FALSE;
                    if ("on".equalsIgnoreCase(value)) return Boolean.TRUE;
                    break;
                case 3:
                    if ("yes".equalsIgnoreCase(value)) return Boolean.TRUE;
                    if ("off".equalsIgnoreCase(value)) return Boolean.FALSE;
                    break;
                case 4:
                    if ("true".equalsIgnoreCase(value)) return Boolean.TRUE;
                    break;
                case 5:
                    if ("false".equalsIgnoreCase(value)) return Boolean.FALSE;
                    break;
            }
            return null;
        }
    }
}
