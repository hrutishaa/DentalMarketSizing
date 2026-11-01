-- name: rows_per_year
SELECT year, COUNT(*) AS states, SUM(est) AS establishments FROM cbp GROUP BY year ORDER BY year;

-- name: size_class_gap
-- size-class counts don't always add up to the published total (a handful of offices per state
-- have no size class). Measure the gap so we know how much the SAM could be understated.
SELECT year,
       SUM(CASE WHEN est <> (n_lt5 + n5_9 + n10_19 + n20_49 + n50_99 + n100_249 + n250_499 + n500_999 + n1000) THEN 1 ELSE 0 END) AS states_with_gap,
       SUM(est - (n_lt5 + n5_9 + n10_19 + n20_49 + n50_99 + n100_249 + n250_499 + n500_999 + n1000)) AS offices_without_size,
       ROUND(100.0 * SUM(est - (n_lt5 + n5_9 + n10_19 + n20_49 + n50_99 + n100_249 + n250_499 + n500_999 + n1000)) / SUM(est), 3) AS gap_pct
FROM cbp GROUP BY year ORDER BY year;

-- name: unmatched_population
SELECT c.fips FROM cbp c LEFT JOIN pop p ON p.fips = c.fips WHERE c.year = 2023 AND p.fips IS NULL;
