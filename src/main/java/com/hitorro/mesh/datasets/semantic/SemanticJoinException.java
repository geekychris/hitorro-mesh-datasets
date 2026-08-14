/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.mesh.datasets.semantic;

/**
 * Thrown when a semantic-join clause like {@code USING PLACE} can't be
 * resolved — no matching relationship declared, unknown table, or ambiguous
 * source. The message is written for the SQL author, not the JVM console,
 * so it should identify the exact clause that failed and (where possible)
 * what to write instead.
 */
public class SemanticJoinException extends RuntimeException {
    public SemanticJoinException(String message) { super(message); }
}
