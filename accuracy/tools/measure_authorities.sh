#!/usr/bin/env bash
# Measures the app's prayer-time profiles against authorities' own published years
# (accuracy/results/authorities_measurements.md). Run from the repository root.
#   1. (optional) refresh the official samples:  python3 accuracy/tools/fetch_authority_tables.py 2026
#   2. this script: writes accuracy/results/authorities_measurements_table.md and prints the per-prayer fits
set -euo pipefail
./gradlew --no-parallel -Dorg.gradle.jvmargs=-Xmx2g :core:prayer:test \
  --tests '*AuthorityMeasure*' --tests '*AuthorityFit*' -Pnoor.research=true -i \
  | grep -E '^    ' | grep -v 'Input property\|and more' || true
echo "table: accuracy/results/authorities_measurements_table.md"
