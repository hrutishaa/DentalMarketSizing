package sizing;

import java.util.*;

/**
 * Weighted scoring used to rank launch states. Each criterion is turned into a percentile rank
 * (0 = lowest state, 1 = highest) before weighting, so California's size doesn't flatten everyone else
 * the way plain min-max scaling would.
 */
public final class Scoring {
    private Scoring() {}

    public static final String[] CRITERIA = {"sam_est", "sam_growth_pct", "payroll_per_est", "sam_per_100k"};

    /** Named weight sets. "base" is the one used for the recommendation; the rest test how stable it is. */
    public static final Map<String, double[]> WEIGHTS = new LinkedHashMap<>();
    static {
        WEIGHTS.put("base", new double[]{0.40, 0.20, 0.20, 0.20});
        WEIGHTS.put("equal", new double[]{0.25, 0.25, 0.25, 0.25});
        WEIGHTS.put("scale heavy", new double[]{0.60, 0.10, 0.15, 0.15});
        WEIGHTS.put("growth heavy", new double[]{0.25, 0.40, 0.15, 0.20});
        WEIGHTS.put("ability to pay heavy", new double[]{0.25, 0.15, 0.40, 0.20});
    }

    /** Percentile rank in 0..1. Tied values share the average of their positions. */
    public static double[] percentileRank(double[] v) {
        int n = v.length;
        double[] out = new double[n];
        if (n == 1) { out[0] = 0.5; return out; }
        for (int i = 0; i < n; i++) {
            int below = 0, equal = 0;
            for (double x : v) {
                if (x < v[i]) below++;
                else if (x == v[i]) equal++;
            }
            out[i] = (below + (equal - 1) / 2.0) / (n - 1);
        }
        return out;
    }

    /** Returns a 0..100 score per row, in the same order as the input rows. */
    public static double[] score(List<Map<String, Object>> rows, double[] weights) {
        double sumW = Arrays.stream(weights).sum();
        double[] total = new double[rows.size()];
        for (int c = 0; c < CRITERIA.length; c++) {
            double[] raw = new double[rows.size()];
            for (int i = 0; i < rows.size(); i++) raw[i] = ((Number) rows.get(i).get(CRITERIA[c])).doubleValue();
            double[] norm = percentileRank(raw);
            for (int i = 0; i < rows.size(); i++) total[i] += 100 * weights[c] / sumW * norm[i];
        }
        return total;
    }

    /** Indexes of the rows sorted by score, best first. Ties keep input order. */
    public static List<Integer> ranking(double[] score) {
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < score.length; i++) idx.add(i);
        idx.sort((a, b) -> Double.compare(score[b], score[a]));
        return idx;
    }

    public static double round(double v, int places) {
        double f = Math.pow(10, places);
        return Math.round(v * f) / f;
    }
}
