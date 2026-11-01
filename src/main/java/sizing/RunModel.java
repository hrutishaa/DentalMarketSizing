package sizing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class RunModel {
    static final int LAUNCH_STATES = 10;
    /** Share of the launch-state SAM a new entrant could win in 3 years. Planning assumptions, not data. */
    static final double[] SOM_SHARES = {0.01, 0.02, 0.05};

    public static void main(String[] args) throws Exception {
        Map<String, Object> out = new LinkedHashMap<>();
        try (Db db = new Db(Path.of("sql"))) {
            db.createTables();
            int r23 = db.loadCbp(Path.of("data/cbp23st-621210.csv"), 2023);
            int r18 = db.loadCbp(Path.of("data/cbp18st-621210.csv"), 2018);
            db.loadPopulation(Path.of("data/NST-EST2025-ALLDATA.csv"));
            db.loadPricing(Path.of("data/pricing.csv"));

            Map<String, List<Map<String, Object>>> checks = db.run("00_checks.sql");
            boolean gapTooBig = checks.get("size_class_gap").stream().anyMatch(r -> d(r, "gap_pct") > 0.5);
            if (gapTooBig || !checks.get("unmatched_population").isEmpty())
                throw new IllegalStateException("data checks failed: " + checks);
            out.put("source", "U.S. Census Bureau County Business Patterns 2018 and 2023 (NAICS 621210, Offices of Dentists); "
                    + "Census Vintage 2025 state population estimates; Open Dental public price list");
            out.put("state_rows", Map.of("2023", r23, "2018", r18));
            out.put("checks", checks.get("rows_per_year"));
            out.put("size_class_gap", checks.get("size_class_gap"));

            Map<String, List<Map<String, Object>>> market = db.run("01_market.sql");
            List<Map<String, Object>> nat = market.get("national");
            Map<String, Object> n18 = nat.get(0), n23 = nat.get(1);
            Map<String, Object> national = new LinkedHashMap<>();
            national.put("establishments_2023", n23.get("establishments"));
            national.put("establishments_2018", n18.get("establishments"));
            national.put("establishment_change_pct", pct(n23, n18, "establishments"));
            national.put("employees_2023", n23.get("employees"));
            national.put("payroll_2023_usd", 1000L * ((Number) n23.get("payroll_k")).longValue());
            national.put("sam_establishments_2023", n23.get("est_5_49"));
            national.put("sam_share_pct", Scoring.round(100.0 * d(n23, "est_5_49") / d(n23, "establishments"), 1));
            national.put("change_lt5_pct", pct(n23, n18, "est_lt5"));
            national.put("change_5_49_pct", pct(n23, n18, "est_5_49"));
            national.put("change_50_plus_pct", pct(n23, n18, "est_50_plus"));
            national.put("avg_payroll_per_establishment_usd", Math.round(1000.0 * d(n23, "payroll_k") / d(n23, "establishments")));
            out.put("national", national);

            List<Map<String, Object>> mix = new ArrayList<>();
            for (int i = 0; i < market.get("size_mix_2023").size(); i++) {
                Map<String, Object> a = market.get("size_mix_2023").get(i), b = market.get("size_mix_2018").get(i);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("size_class", a.get("size_class"));
                m.put("est_2018", b.get("establishments"));
                m.put("est_2023", a.get("establishments"));
                m.put("change_pct", Scoring.round(100.0 * (d(a, "establishments") - d(b, "establishments")) / d(b, "establishments"), 1));
                m.put("share_2023_pct", Scoring.round(100.0 * d(a, "establishments") / d(n23, "establishments"), 1));
                mix.add(m);
            }
            out.put("size_mix", mix);

            List<Map<String, Object>> tamSam = new ArrayList<>();
            for (Map<String, Object> r : market.get("tam_sam")) {
                Map<String, Object> m = new LinkedHashMap<>(r);
                m.put("tam_usd", Math.round(d(r, "tam_usd")));
                m.put("sam_usd", Math.round(d(r, "sam_usd")));
                tamSam.add(m);
            }
            out.put("tam_sam", tamSam);
            out.put("pricing", db.query("SELECT * FROM pricing ORDER BY monthly_price"));

            // state scoring
            List<Map<String, Object>> states = db.run("02_states.sql").get("state_features");
            Map<String, List<String>> topByWeights = new LinkedHashMap<>();
            double[] baseScore = null;
            for (Map.Entry<String, double[]> w : Scoring.WEIGHTS.entrySet()) {
                double[] s = Scoring.score(states, w.getValue());
                if (baseScore == null) baseScore = s;
                List<String> top = new ArrayList<>();
                for (int i : Scoring.ranking(s).subList(0, LAUNCH_STATES)) top.add((String) states.get(i).get("state"));
                topByWeights.put(w.getKey(), top);
            }
            List<Integer> order = Scoring.ranking(baseScore);
            List<Map<String, Object>> ranked = new ArrayList<>();
            int rank = 0;
            for (int i : order) {
                Map<String, Object> st = states.get(i);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("rank", ++rank);
                m.put("state", st.get("state"));
                m.put("score", Scoring.round(baseScore[i], 1));
                m.put("sam_est", st.get("sam_est"));
                m.put("sam_growth_pct", Scoring.round(d(st, "sam_growth_pct"), 1));
                m.put("payroll_per_est", Math.round(d(st, "payroll_per_est")));
                m.put("sam_per_100k", Scoring.round(d(st, "sam_per_100k"), 1));
                m.put("est_all", st.get("est_all"));
                ranked.add(m);
            }
            out.put("states", ranked);
            out.put("weights", Scoring.WEIGHTS);
            out.put("top10_by_weights", topByWeights);

            List<String> baseTop = topByWeights.get("base");
            List<String> always = new ArrayList<>(baseTop);
            for (List<String> t : topByWeights.values()) always.retainAll(t);
            Map<String, Object> stability = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> e : topByWeights.entrySet()) {
                long kept = e.getValue().stream().filter(baseTop::contains).count();
                stability.put(e.getKey(), kept);
            }
            out.put("top10_overlap_with_base", stability);
            out.put("top10_in_every_weight_set", always);

            // SOM: launch states only
            long launchSam = ranked.subList(0, LAUNCH_STATES).stream().mapToLong(m -> ((Number) m.get("sam_est")).longValue()).sum();
            long samTotal = ((Number) n23.get("est_5_49")).longValue();
            Map<String, Object> som = new LinkedHashMap<>();
            som.put("launch_states", baseTop);
            som.put("launch_sam_establishments", launchSam);
            som.put("launch_share_of_sam_pct", Scoring.round(100.0 * launchSam / samTotal, 1));
            List<Map<String, Object>> grid = new ArrayList<>();
            for (Map<String, Object> p : market.get("tam_sam")) {
                for (double share : SOM_SHARES) {
                    Map<String, Object> g = new LinkedHashMap<>();
                    g.put("scenario", p.get("scenario"));
                    g.put("monthly_price", p.get("monthly_price"));
                    g.put("share_pct", share * 100);
                    long offices = Math.round(launchSam * share);
                    g.put("offices", offices);
                    g.put("arr_usd", Math.round(offices * d(p, "monthly_price") * 12));
                    grid.add(g);
                }
            }
            som.put("grid", grid);
            out.put("som", som);
        }

        ObjectMapper om = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        String json = om.writeValueAsString(out);
        Files.writeString(Path.of("docs/results.json"), json + "\n");
        Files.writeString(Path.of("docs/results.js"), "window.RESULTS = " + json + ";\n");
        System.out.println(om.writeValueAsString(Map.of("national", out.get("national"), "tam_sam", out.get("tam_sam"),
                "top10", out.get("top10_by_weights"), "overlap", out.get("top10_overlap_with_base"),
                "always", out.get("top10_in_every_weight_set"), "som", out.get("som"), "mix", out.get("size_mix"))));
        System.out.println(((List<?>) out.get("states")).subList(0, 12));
    }

    static double d(Map<String, Object> m, String k) {
        return ((Number) m.get(k)).doubleValue();
    }

    static double pct(Map<String, Object> now, Map<String, Object> before, String k) {
        return Scoring.round(100.0 * (d(now, k) - d(before, k)) / d(before, k), 1);
    }
}
