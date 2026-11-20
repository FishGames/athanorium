package io.github.fishgames.athanorium.core.slot;

import io.github.fishgames.athanorium.core.id.Id;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Result of the last computation of a state: passed or failed with reason, the metrics and the two checksums
 * the computation was made with.
 */
public record CalcResult(boolean passed, String failKey, String failDetail, Map<Id, Long> metrics,
                         String solvability, String scoring) {
    public CalcResult {
        metrics = Collections.unmodifiableMap(new TreeMap<>(metrics));
        failKey = failKey == null ? "" : failKey;
        failDetail = failDetail == null ? "" : failDetail;
    }

    public static CalcResult failed(String key, String detail, String solvability, String scoring) {
        return new CalcResult(false, key, detail, Map.of(), solvability, scoring);
    }

    public CalcResult withMetric(Id metric, long value, String newScoring) {
        Map<Id, Long> m = new TreeMap<>(metrics);
        m.put(metric, value);
        return new CalcResult(passed, failKey, failDetail, m, solvability, newScoring);
    }
}
