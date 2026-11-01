-- name: state_features
-- one row per state: size of the target segment, its 5-year growth, payroll per office
-- (a proxy for practice size and ability to pay) and target offices per 100k residents
SELECT p.name AS state,
       c23.fips,
       c23.est AS est_all,
       (c23.n5_9 + c23.n10_19 + c23.n20_49) AS sam_est,
       (c18.n5_9 + c18.n10_19 + c18.n20_49) AS sam_est_2018,
       100.0 * ((c23.n5_9 + c23.n10_19 + c23.n20_49) - (c18.n5_9 + c18.n10_19 + c18.n20_49))
             / (c18.n5_9 + c18.n10_19 + c18.n20_49) AS sam_growth_pct,
       1000.0 * c23.payroll_k / c23.est AS payroll_per_est,
       100000.0 * (c23.n5_9 + c23.n10_19 + c23.n20_49) / p.pop2023 AS sam_per_100k,
       p.pop2023
FROM cbp c23
JOIN cbp c18 ON c18.fips = c23.fips AND c18.year = 2018
JOIN pop p ON p.fips = c23.fips
WHERE c23.year = 2023
ORDER BY sam_est DESC;
