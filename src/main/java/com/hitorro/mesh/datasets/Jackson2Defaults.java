/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.mesh.datasets;

import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamWriteConstraints;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Builders for this library's mappers with Jackson 2 behaviour, as in 3.x (Jackson 2.21).
 *
 * <p>The same convention as hitorro-core's {@code JsonMappers}, kept here because this library
 * does not depend on hitorro-core: Jackson 2 defaults ({@code configureForJackson2()} on the
 * factory and the mapper) plus Jackson 2's stream limits. Jackson 3 lowered the nesting depth
 * from 1000 to 500 and raised the string length from 20,000,000 to 100,000,000 chars, and
 * {@code builderWithJackson2Defaults()} restores neither.
 */
public final class Jackson2Defaults {

    /** Jackson 2.18/2.21 default read limits (same values as hitorro-core's JsonMappers). */
    public static final StreamReadConstraints READ_CONSTRAINTS = StreamReadConstraints.builder()
            .maxNestingDepth(1000)
            .maxStringLength(20_000_000)
            .maxNumberLength(1000)
            .maxNameLength(50_000)
            .maxDocumentLength(-1L)
            .maxTokenCount(-1L)
            .build();

    /** Jackson 2.18/2.21 default write limits. */
    public static final StreamWriteConstraints WRITE_CONSTRAINTS = StreamWriteConstraints.builder()
            .maxNestingDepth(1000)
            .build();

    private Jackson2Defaults() { }

    /**
     * A JSON mapper builder: {@code JsonMapper.builderWithJackson2Defaults()} plus Jackson 2 limits and
     * {@link Jackson2JdkModule} (Locale, Month, {@code byte[]} trees as in Jackson 2).
     */
    public static JsonMapper.Builder json() {
        JsonFactory f = JsonFactory.builderWithJackson2Defaults()
                .streamReadConstraints(READ_CONSTRAINTS)
                .streamWriteConstraints(WRITE_CONSTRAINTS)
                .build();
        return JsonMapper.builder(f).configureForJackson2()
                .addModule(new Jackson2JdkModule());
    }

    /**
     * A YAML mapper builder that reads and writes YAML as Jackson 2 did: {@link YamlMappers#builder()}
     * (YAML 1.1 scalar resolution, EMPTY_STRING_AS_NULL, Jackson 2 defaults and limits).
     */
    public static YAMLMapper.Builder yaml() {
        return YamlMappers.builder();
    }
}
