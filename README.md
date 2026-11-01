# Dental Market Sizing

If you were launching practice-management software for U.S. dental offices, how big is the market, which offices should you go after, and which states should the first sales hires cover? This project answers that with public Census data instead of a guess, and keeps every assumption out in the open so it's easy to argue with.

**Live dashboard:** https://hrutishaa.github.io/DentalMarketSizing/

## Data

- **County Business Patterns (CBP) 2018 and 2023**, U.S. Census Bureau. State files [`cbp18st.zip`](https://www2.census.gov/programs-surveys/cbp/datasets/2018/cbp18st.zip) and [`cbp23st.zip`](https://www2.census.gov/programs-surveys/cbp/datasets/2023/cbp23st.zip). I keep only NAICS 621210 (Offices of Dentists), all legal forms, for 50 states + DC: establishments, employees, annual payroll and establishment counts by employee-size class. The full files are ~90 MB each, so `scripts/extract-cbp.sh` downloads them and writes the two small extracts in `data/`.
- **Vintage 2025 state population estimates** ([`NST-EST2025-ALLDATA.csv`](https://www2.census.gov/programs-surveys/popest/datasets/2020-2025/state/totals/NST-EST2025-ALLDATA.csv)), U.S. Census Bureau, committed unchanged. I use the 2023 estimate to match the CBP year.
- **Price points** come from Open Dental's published fee page ([opendental.com/site/fees.html](https://www.opendental.com/site/fees.html), checked November 2026), which is one of the few vendors in this space that posts prices. See `data/pricing.csv`.

Census data is public domain. An establishment in CBP is a single physical location, which lines up nicely with per-office software pricing.

## Approach

```
data/*.csv -> SQLite (JDBC)
  sql/00_checks.sql   row counts, size classes vs totals, state join
  sql/01_market.sql   national totals, size mix 2018 vs 2023, TAM and SAM per price scenario
  sql/02_states.sql   per-state features for the launch ranking
  -> Java: percentile scoring, weight sensitivity, SOM grid
  -> docs/results.json -> docs/index.html
```

Definitions:

- **TAM** = every dental office x 12 x monthly price.
- **SAM** = offices with 5 to 49 employees. Under 5 is mostly solo practices that are slow to switch and price sensitive; 50+ are large group and DSO sites that buy centrally through enterprise deals. Both are real markets, just not the one a new entrant can sell into first.
- **SOM** = SAM in the 10 launch states x a capture share. The share (1%, 2% or 5% over three years) is a planning assumption, so the output is a grid, not a single number.

**Launch-state scoring.** Four criteria per state: target offices (scale), five-year growth in target offices (momentum), payroll per office (a stand-in for practice size and ability to pay), and target offices per 100k residents (how dense a sales territory is). Each one is turned into a percentile rank before weighting. I started with min-max scaling and California's size flattened everyone else, which pushed North Dakota into the top 10 on payroll alone. Percentile ranks fixed that. The base weights are 40/20/20/20, and four other weight sets check whether the shortlist holds up.

```
mvn test        # scoring math and SQL checked on a small fixture
mvn exec:java   # full run, about a second
```

Java 17, Maven, SQLite through JDBC, JUnit 5. CI runs both and fails if the output drifts from the committed `docs/results.json`.

## Findings

1. **The market is flat in count but consolidating fast.** There were 135,665 dental offices in 2023, down 0.6% from 136,550 in 2018. Offices with under 5 employees fell 4.6% and 5 to 9 fell 7.5%, while 10 to 19 grew 15.4%, 20 to 49 grew 27.1% and 50+ grew 54.6% (from a small base: 315 to 487).
2. **TAM is about $324M a year at $199 per office per month**, with a range of $243M (at $149) to $593M (at $364 with add-ons). That is a subscription market, not a giant one, which matters for how much sales cost a new entrant can carry.
3. **SAM is 83,979 offices (61.9% of all offices), or about $201M a year at the base price.** The 10 to 49 employee part of the SAM is the part that's growing.
4. **Launch states: Washington, North Carolina, Virginia, California, Massachusetts, Florida, Michigan, Texas, New Jersey and South Carolina.** Together they hold 40,737 target offices, 48.5% of the national SAM. Washington ranks first on a mix of density (33.6 target offices per 100k residents) and payroll per office ($553,939); North Carolina and Virginia both grew target offices 7.9% since 2018.
5. **The shortlist is fairly stable.** The equal, scale-heavy and growth-heavy weight sets keep 8, 9 and 8 of the 10 states. Weighting ability to pay most heavily keeps only 5, pulling in smaller high-payroll states like Minnesota and New Hampshire. Washington, North Carolina, Virginia and Massachusetts make the top 10 under all five weight sets, so those are the safest first territories.
6. **SOM:** winning 2% of the launch-state SAM in three years would be 815 offices and about $1.95M ARR at $199, or $1.46M to $3.56M across the price scenarios. Even at 5% it's $4.86M at the base price, so a launch plan would need either the add-on bundle or a fast move up into multi-office groups to reach meaningful scale.

## Caveats

- CBP size-class counts don't always add up to the published total. The gap is 57 offices nationally in 2023 (0.042%) and 47 in 2018, so it can't move the SAM in any way that matters, but the check runs every time.
- CBP counts employer establishments only. Offices with no paid employees are not in this data, so the TAM leaves them out.
- Payroll per office is a proxy for ability to pay, not revenue. It also reflects local wage levels, which is part of why California scores low on it.
- One subscription per office is a simplification. Real pricing often adds per-provider fees, which would push the numbers up for bigger offices.
- The capture shares and the 5 to 49 employee cut are choices, not findings. Both are easy to change in `RunModel.java` and `sql/01_market.sql`.

## Layout

```
data/                    CBP extracts, population estimates, pricing inputs
scripts/extract-cbp.sh   rebuilds the CBP extracts from the Census site
sql/                     checks, market and state queries
src/main/java/sizing/    loader, scoring, model runner
src/test/java/sizing/    JUnit 5 tests
docs/                    dashboard + results.json
```
