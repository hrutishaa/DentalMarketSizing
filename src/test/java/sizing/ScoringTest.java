package sizing;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ScoringTest {
    @Test
    void percentileRankSpreadsZeroToOne() {
        assertArrayEquals(new double[]{0, 0.5, 1}, Scoring.percentileRank(new double[]{3, 10, 500}), 1e-9);
    }

    @Test
    void outlierDoesNotSquashTheRest() {
        // with min-max, 1 and 2 would both be ~0 next to 1000; percentile ranks keep them apart
        double[] r = Scoring.percentileRank(new double[]{1, 2, 1000});
        assertEquals(0.5, r[1], 1e-9);
    }

    @Test
    void tiesShareTheAveragePosition() {
        double[] r = Scoring.percentileRank(new double[]{5, 5, 9});
        assertEquals(r[0], r[1], 1e-9);
        assertEquals(0.25, r[0], 1e-9);
    }

    @Test
    void scoreUsesWeights() {
        List<Map<String, Object>> rows = List.of(
                row(100, 0, 0, 0),
                row(0, 100, 100, 100));
        double[] s = Scoring.score(rows, new double[]{1, 0, 0, 0});
        assertEquals(100, s[0], 1e-9);
        assertEquals(0, s[1], 1e-9);
        s = Scoring.score(rows, new double[]{0.4, 0.2, 0.2, 0.2});
        assertEquals(40, s[0], 1e-9);
        assertEquals(60, s[1], 1e-9);
        assertEquals(List.of(1, 0), Scoring.ranking(s));
    }

    @Test
    void weightSetsAllSumToOne() {
        for (double[] w : Scoring.WEIGHTS.values()) assertEquals(1.0, Arrays.stream(w).sum(), 1e-9);
    }

    private static Map<String, Object> row(double a, double b, double c, double d) {
        Map<String, Object> m = new HashMap<>();
        m.put("sam_est", a);
        m.put("sam_growth_pct", b);
        m.put("payroll_per_est", c);
        m.put("sam_per_100k", d);
        return m;
    }
}
