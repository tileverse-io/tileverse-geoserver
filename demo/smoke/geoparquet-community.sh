#!/usr/bin/env bash
#
# Smoke check for GeoServer's community GeoParquet module, against a running demo (`make demo-up`):
# WFS counts the features of the layers published in the geoparquet-community workspace, from the
# local directory and from S3. The counts hold after the stores are migrated to the parquetry
# plugin, because a migrated layer keeps its name. Needs no DuckDB on the host.

set -euo pipefail

SMOKE_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=demo/smoke/lib.sh
. "$SMOKE_DIR/lib.sh"

WORKSPACE=geoparquet-community

require_geoserver

# Counts the features of geoparquet-community:<layer> and requires the expected number.
count_features() {
    local layer=$1 expected=$2 hits
    hits=$(curl -s "$BASE/$WORKSPACE/wfs?service=WFS&version=2.0.0&request=GetFeature\
&typeNames=$WORKSPACE:$layer&resultType=hits" \
        | grep -oE 'numberMatched="[0-9]+"' | grep -oE '[0-9]+' || true)
    echo "$WORKSPACE:$layer numberMatched=${hits:-<none>}"
    [ "$hits" = "$expected" ] || fail "$WORKSPACE:$layer expected $expected, got '${hits:-<none>}'"
}

# The ne store reads the local directory, and the ne-s3 store reads the s3proxy emulator.
count_features countries "$COUNTRIES"
count_features populated_places 1251
count_features coastlines 1428
count_features boundary_lines_land 390

echo "Community GeoParquet checks passed."
