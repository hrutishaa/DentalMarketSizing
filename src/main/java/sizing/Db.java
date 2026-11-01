package sizing;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

/** In-memory SQLite with a few helpers for loading the Census files and running the .sql steps. */
public class Db implements AutoCloseable {
    private final Connection conn;
    private final Path sqlDir;

    public Db(Path sqlDir) throws SQLException {
        this.conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        this.sqlDir = sqlDir;
    }

    static final String[] SIZE_COLS = {"n<5", "n5_9", "n10_19", "n20_49", "n50_99", "n100_249", "n250_499", "n500_999", "n1000"};
    static final String[] SIZE_NAMES = {"n_lt5", "n5_9", "n10_19", "n20_49", "n50_99", "n100_249", "n250_499", "n500_999", "n1000"};

    public void createTables() throws SQLException {
        try (Statement s = conn.createStatement()) {
            s.execute("CREATE TABLE cbp (year INTEGER, fips TEXT, est INTEGER, emp INTEGER, payroll_k INTEGER, "
                    + String.join(" INTEGER, ", SIZE_NAMES) + " INTEGER)");
            s.execute("CREATE TABLE pop (fips TEXT PRIMARY KEY, name TEXT, pop2023 INTEGER)");
            s.execute("CREATE TABLE pricing (scenario TEXT, monthly_price REAL, basis TEXT)");
        }
    }

    /** Loads one CBP state file (already filtered to NAICS 621210, all legal forms). Returns rows read. */
    public int loadCbp(Path file, int year) throws IOException, SQLException {
        String sql = "INSERT INTO cbp VALUES (?,?,?,?,?" + ",?".repeat(SIZE_NAMES.length) + ")";
        int n = 0;
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (CSVRecord rec : CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(r)) {
                if (!"621210".equals(rec.get("naics")) || !"-".equals(rec.get("lfo"))) continue;
                ps.setInt(1, year);
                ps.setString(2, rec.get("fipstate"));
                ps.setLong(3, num(rec.get("est")));
                ps.setLong(4, num(rec.get("emp")));
                ps.setLong(5, num(rec.get("ap")));
                for (int i = 0; i < SIZE_COLS.length; i++) ps.setLong(6 + i, num(rec.get(SIZE_COLS[i])));
                ps.addBatch();
                n++;
            }
            ps.executeBatch();
        }
        return n;
    }

    /** Loads state rows (SUMLEV 040) from the Census population estimates file. */
    public int loadPopulation(Path file) throws IOException, SQLException {
        int n = 0;
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.ISO_8859_1);
             PreparedStatement ps = conn.prepareStatement("INSERT INTO pop VALUES (?,?,?)")) {
            for (CSVRecord rec : CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(r)) {
                if (!"040".equals(rec.get("SUMLEV"))) continue;
                ps.setString(1, rec.get("STATE"));
                ps.setString(2, rec.get("NAME"));
                ps.setLong(3, num(rec.get("POPESTIMATE2023")));
                ps.addBatch();
                n++;
            }
            ps.executeBatch();
        }
        return n;
    }

    public int loadPricing(Path file) throws IOException, SQLException {
        int n = 0;
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8);
             PreparedStatement ps = conn.prepareStatement("INSERT INTO pricing VALUES (?,?,?)")) {
            for (CSVRecord rec : CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(r)) {
                ps.setString(1, rec.get("scenario"));
                ps.setDouble(2, Double.parseDouble(rec.get("monthly_price_usd")));
                ps.setString(3, rec.get("basis"));
                ps.addBatch();
                n++;
            }
            ps.executeBatch();
        }
        return n;
    }

    static long num(String s) {
        s = s == null ? "" : s.trim();
        return s.isEmpty() || s.equals("N") ? 0 : Long.parseLong(s);
    }

    /** Runs every block in a script. Blocks that return rows are collected by name. */
    public Map<String, List<Map<String, Object>>> run(String script) throws IOException, SQLException {
        Map<String, List<Map<String, Object>>> out = new LinkedHashMap<>();
        for (SqlScript.Block b : SqlScript.read(sqlDir.resolve(script))) {
            try (Statement s = conn.createStatement()) {
                if (s.execute(b.sql())) out.put(b.name(), rows(s.getResultSet()));
            }
        }
        return out;
    }

    public List<Map<String, Object>> query(String sql) throws SQLException {
        try (Statement s = conn.createStatement()) {
            return rows(s.executeQuery(sql));
        }
    }

    private static List<Map<String, Object>> rows(ResultSet rs) throws SQLException {
        List<Map<String, Object>> out = new ArrayList<>();
        ResultSetMetaData md = rs.getMetaData();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= md.getColumnCount(); i++) row.put(md.getColumnLabel(i), rs.getObject(i));
            out.add(row);
        }
        return out;
    }

    @Override
    public void close() throws SQLException {
        conn.close();
    }
}
