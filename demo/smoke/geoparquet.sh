#!/usr/bin/env bash
#
# Smoke check for the GeoParquet WFS output format, against a running demo (`make demo-up`).
#
# Reading the response back proves the plugin zip ships everything the GeoParquet writer needs,
# which the module's integration tests cannot show: those run against the Maven classpath, where
# a jar missing from the assembly still resolves.

set -euo pipefail

SMOKE_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=demo/smoke/lib.sh
. "$SMOKE_DIR/lib.sh"

require_duckdb
require_geoserver

for ws in "${WORKSPACES[@]}"; do
    out=/tmp/$ws-countries.parquet
    download "$(getfeature_url "$ws")&outputFormat=geoparquet" \
        "$out" application/vnd.apache.parquet "$ws geoparquet"

    got=$(query "load spatial; $SUMMARY_SELECT read_parquet('$out')")
    echo "$ws:countries as geoparquet -> $got"
    [ "$got" = "$WORLD" ] || fail "$ws geoparquet summarised as '$got', wanted '$WORLD'"

    # The footer's "geo" entry is what separates GeoParquet from plain Parquet. The writer emits
    # it by default, alongside the native geometry logical type.
    geo=$(query "select count(*) from parquet_kv_metadata('$out') where key::varchar = 'geo'")
    [ "$geo" = "1" ] || fail "$ws geoparquet has no 'geo' footer metadata (got '$geo')"
done

# format_options reach the writer: ask for a codec the writer would not choose on its own, and
# read back what landed in the footer. Snappy earns the check where zstd could not, the writer
# already defaulting to zstd. The module's integration tests pin the option plumbing without
# asserting the codec.
snappy=/tmp/countries-snappy.parquet
download "$(getfeature_url parquetry)&outputFormat=geoparquet&format_options=compression:snappy" \
    "$snappy" application/vnd.apache.parquet "geoparquet compression:snappy"
codec=$(query "select distinct compression from parquet_metadata('$snappy')")
echo "format_options compression:snappy -> $codec"
[ "$codec" = "SNAPPY" ] || fail "format_options compression:snappy produced '$codec'"

echo "GeoParquet output format checks passed."
