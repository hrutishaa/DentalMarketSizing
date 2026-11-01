-- name: national
SELECT year,
       SUM(est) AS establishments,
       SUM(emp) AS employees,
       SUM(payroll_k) AS payroll_k,
       SUM(n_lt5) AS est_lt5,
       SUM(n5_9 + n10_19 + n20_49) AS est_5_49,
       SUM(n50_99 + n100_249 + n250_499 + n500_999 + n1000) AS est_50_plus
FROM cbp GROUP BY year ORDER BY year;

-- name: size_mix_2023
SELECT 'Under 5' AS size_class, SUM(n_lt5) AS establishments FROM cbp WHERE year = 2023
UNION ALL SELECT '5 to 9', SUM(n5_9) FROM cbp WHERE year = 2023
UNION ALL SELECT '10 to 19', SUM(n10_19) FROM cbp WHERE year = 2023
UNION ALL SELECT '20 to 49', SUM(n20_49) FROM cbp WHERE year = 2023
UNION ALL SELECT '50 to 99', SUM(n50_99) FROM cbp WHERE year = 2023
UNION ALL SELECT '100 or more', SUM(n100_249 + n250_499 + n500_999 + n1000) FROM cbp WHERE year = 2023;

-- name: size_mix_2018
SELECT 'Under 5' AS size_class, SUM(n_lt5) AS establishments FROM cbp WHERE year = 2018
UNION ALL SELECT '5 to 9', SUM(n5_9) FROM cbp WHERE year = 2018
UNION ALL SELECT '10 to 19', SUM(n10_19) FROM cbp WHERE year = 2018
UNION ALL SELECT '20 to 49', SUM(n20_49) FROM cbp WHERE year = 2018
UNION ALL SELECT '50 to 99', SUM(n50_99) FROM cbp WHERE year = 2018
UNION ALL SELECT '100 or more', SUM(n100_249 + n250_499 + n500_999 + n1000) FROM cbp WHERE year = 2018;

-- name: tam_sam
-- one subscription per establishment (a physical office), billed monthly
SELECT p.scenario, p.monthly_price,
       t.est * p.monthly_price * 12 AS tam_usd,
       t.est_5_49 * p.monthly_price * 12 AS sam_usd
FROM pricing p,
     (SELECT SUM(est) AS est, SUM(n5_9 + n10_19 + n20_49) AS est_5_49 FROM cbp WHERE year = 2023) t
ORDER BY p.monthly_price;
