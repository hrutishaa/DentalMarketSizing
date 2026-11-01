package sizing;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SqlTest {
    @Test
    void loaderKeepsOnlyDentistsAllLegalForms() throws Exception {
        try (Db db = new Db(Path.of("sql"))) {
            db.createTables();
            assertEquals(1, db.loadCbp(Path.of("src/test/resources/cbp-fixture.csv"), 2023));
        }
    }

    @Test
    void tamCountsEveryOfficeAndSamOnlyFiveToFortyNine() throws Exception {
        try (Db db = new Db(Path.of("sql"))) {
            db.createTables();
            db.loadCbp(Path.of("src/test/resources/cbp-fixture.csv"), 2023);
            db.loadPricing(Path.of("src/test/resources/pricing-fixture.csv"));
            List<Map<String, Object>> r = db.run("01_market.sql").get("tam_sam");
            assertEquals(1, r.size());
            // 10 offices, 6 of them with 5 to 49 employees, $100 a month
            assertEquals(12_000.0, ((Number) r.get(0).get("tam_usd")).doubleValue(), 1e-6);
            assertEquals(7_200.0, ((Number) r.get(0).get("sam_usd")).doubleValue(), 1e-6);
        }
    }

    @Test
    void suppressedValuesReadAsZero() {
        assertEquals(0, Db.num("N"));
        assertEquals(0, Db.num(""));
        assertEquals(42, Db.num("42"));
    }
}
