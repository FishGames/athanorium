package org.example.resourcemod.core.score;

/**
 * Exchangeable placeholder for the yield formula (balancing comes later). It receives test_products already now,
 * because otherwise a higher number of test products would lower the yield.
 */
public interface YieldFormula {
    /** Items per interval for a metric value, the puzzle's reference value and its number of test products. */
    long amountPerInterval(long metricValue, double reference, int testProducts, boolean lowerIsBetter);

    /** Production interval in ticks: the cycles of the test run. */
    default long intervalTicks(long cycles) {
        return cycles;
    }

    /** Simple placeholder: 1 item per test product, scaled by reference / value. */
    YieldFormula PLACEHOLDER = (value, reference, products, lowerIsBetter) -> {
        double ratio = lowerIsBetter ? reference / Math.max(1, value) : value / reference;
        return Math.max(1, Math.round(ratio * products));
    };
}
