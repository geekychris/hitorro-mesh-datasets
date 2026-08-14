/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.mesh.datasets.loader;

import com.hitorro.mesh.datasets.model.License;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Combines the licences of two or more datasets and reports what obligations
 * ride along with a joined result.
 *
 * <p>This is intentionally coarse: it answers <em>"is the join distributable,
 * and what must the consumer preserve?"</em> not <em>"what is the legal
 * status of a specific derivative in your jurisdiction?"</em> — those still
 * need a lawyer.</p>
 *
 * <p>Combination rules used here:</p>
 * <ul>
 *   <li><b>Redistribution</b>: allowed iff every input allows it.</li>
 *   <li><b>Commercial use</b>: allowed iff every input allows it.</li>
 *   <li><b>Attribution</b>: required iff any input requires it — the
 *       consumer must credit each attributed source.</li>
 *   <li><b>Share-alike</b>: obligated iff any input is share-alike (ODbL,
 *       CC-BY-SA). This is the sharpest edge: an ODbL-derived join
 *       drags the whole result under ODbL when it's a "derivative database".</li>
 *   <li><b>Modification</b>: allowed iff every input allows it.</li>
 * </ul>
 */
public final class LicenseAlgebra {

    private LicenseAlgebra() { }

    /** The result of combining two or more licences. */
    public record Result(
            boolean redistribution,
            boolean commercialUse,
            boolean attributionRequired,
            boolean shareAlike,
            boolean modification,
            List<String> attributions,
            List<String> warnings
    ) {

        /**
         * @return true iff every capability is preserved and no share-alike
         *         obligation has kicked in. Not the same as "legally safe" —
         *         it's a soft "green light for straightforward reuse".
         */
        public boolean fullyPermissive() {
            return redistribution && commercialUse && !shareAlike && modification;
        }
    }

    public static Result combine(List<License> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            throw new IllegalArgumentException("no licences to combine");
        }
        boolean redist = true, commercial = true, mod = true;
        boolean attribReq = false, shareAlike = false;
        List<String> attribs = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (License l : inputs) {
            redist &= l.redistribution();
            commercial &= l.commercialUse();
            mod &= l.modification();
            attribReq |= l.attributionRequired();
            shareAlike |= l.shareAlike();
            if (l.attribution() != null && !l.attribution().isBlank()) {
                attribs.add(l.attribution() + " (" + l.spdx() + ")");
            }
            if (l.shareAlike()) {
                warnings.add(l.spdx() + " is share-alike — the joined result "
                        + "may inherit " + l.spdx() + " when redistributed as a database.");
            }
            if (!l.commercialUse()) {
                warnings.add(l.spdx() + " forbids commercial use — the join is "
                        + "non-commercial only.");
            }
        }

        return new Result(
                redist, commercial, attribReq, shareAlike, mod,
                attribs.stream().distinct().collect(Collectors.toList()),
                warnings);
    }
}
