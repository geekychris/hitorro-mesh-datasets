/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.mesh.datasets.model;

/**
 * How records in two datasets relate to each other.
 *
 * <p>The planner picks a physical join strategy per kind:</p>
 * <ul>
 *   <li>{@link #EXACT_ID} — same identifier value in the same namespace.
 *       Direct equi-join; cheap.</li>
 *   <li>{@link #HIERARCHICAL} — parent/child in a well-known containment tree
 *       (city → county → state → country). Chain-join through the hierarchy
 *       table.</li>
 *   <li>{@link #SPATIAL} — geometry predicate: {@code within}, {@code nearest},
 *       {@code intersects}, {@code distance-lt}. Requires spatial index at the
 *       agent; parameters carry the predicate.</li>
 *   <li>{@link #PROBABILISTIC} — entity resolution with a confidence score.
 *       Method (name+geo+parent, embedding, ...) is in the parameters; the
 *       resulting row carries {@code confidence} and {@code evidence} so
 *       {@code CONFIDENCE > x} predicates can filter it.</li>
 * </ul>
 */
public enum RelationshipKind {
    EXACT_ID,
    HIERARCHICAL,
    SPATIAL,
    PROBABILISTIC
}
