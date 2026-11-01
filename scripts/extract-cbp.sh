#!/usr/bin/env bash
# Pulls the dental office rows (NAICS 621210, all legal forms) out of the
# Census County Business Patterns state files. The full files are ~90 MB each,
# so only the extracted rows are committed.
set -euo pipefail
cd "$(dirname "$0")/../data"
for y in 18 23; do
  curl -sSL -o "/tmp/cbp${y}st.zip" "https://www2.census.gov/programs-surveys/cbp/datasets/20${y}/cbp${y}st.zip"
  unzip -o -q "/tmp/cbp${y}st.zip" -d /tmp
  { head -1 "/tmp/cbp${y}st.txt"; grep '^"[0-9]*","621210","-"' "/tmp/cbp${y}st.txt"; } > "cbp${y}st-621210.csv"
done
curl -sSL -o NST-EST2025-ALLDATA.csv \
  https://www2.census.gov/programs-surveys/popest/datasets/2020-2025/state/totals/NST-EST2025-ALLDATA.csv
