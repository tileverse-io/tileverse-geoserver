#!/usr/bin/env bash
#
# Shared helpers for the demo smoke checks. Sourced by the check scripts beside it, not run.
#
# The checks drive a demo started with `make demo-up`. Point them at another instance with
# GEOSERVER_URL, e.g.
#
#   GEOSERVER_URL=http://gs.example.com/geoserver demo/smoke/geoparquet.sh

# The values here are read by the scripts that source this file, which shellcheck cannot see
# when it lints this file on its own.
# shellcheck disable=SC2034

BASE=${GEOSERVER_URL:-http://localhost:8080/geoserver}

# The demo publishes the same countries twice: parquetry reads a local file, parquetry-s3 reads
# from the s3proxy emulator through the AWS default credential chain.
WORKSPACES=(parquetry parquetry-s3)
COUNTRIES=242

fail() {
    echo "SMOKE FAIL: $1" >&2
    exit 1
}

# Echoes the GetFeature URL for one workspace's countries layer.
#
# Deliberately no srsName. WFS 2.0 answers EPSG:4326 in its authority order, latitude first, and
# the export writes longitude first regardless: both the Apache Parquet geospatial types and
# GeoParquet require stored coordinates to be (x, y) with x as easting or longitude, an ordering
# that "explicitly overrides the axis order as specified in the CRS". Leaving srsName unset keeps
# this check on that conversion rather than on a request that asks for longitude order outright.
getfeature_url() {
    local ws=$1
    echo "$BASE/$ws/wfs?service=WFS&version=2.0.0&request=GetFeature&typeNames=$ws:countries"
}

# The output formats need DuckDB to read their responses back: parquet is built into the CLI,
# read_arrow comes from the arrow extension, and decoding geometry needs spatial. Installing an
# extension already present costs nothing, which keeps a local run self-sufficient.
require_duckdb() {
    command -v duckdb >/dev/null \
        || fail "duckdb is not on PATH; install it from https://duckdb.org/docs/installation/"
    duckdb -c "install arrow; install spatial" >/dev/null 2>&1 \
        || fail "could not install the DuckDB arrow and spatial extensions"
}

# Runs one DuckDB query and echoes its single value, or the whole error text when the query
# fails. Either way the caller's comparison decides, and a failure message quotes what DuckDB
# said rather than the last line of its diagnostic.
query() {
    local output
    if output=$(duckdb -noheader -list -c "$1" 2>&1); then
        printf '%s\n' "$output" | tail -1
    else
        printf '%s\n' "$output"
    fi
}

# Fails with a usable message when nothing answers at $BASE. Without this, curl's exit code
# stops the script under `set -e` before any check reports, and "the demo is not running" is
# the likeliest way these scripts fail on a workstation.
require_geoserver() {
    local code
    code=$(curl -s -o /dev/null -m 10 -w '%{http_code}' "$BASE/web/") || code=000
    [ "$code" != "000" ] \
        || fail "no response from GeoServer at $BASE - is the demo running? (make demo-up)"
}

# Downloads a GetFeature response, asserting the status and the media type. Prints the head of
# the body on mismatch, where a WFS ExceptionReport says what actually broke.
download() {
    local url=$1 out=$2 want=$3 label=$4 meta code media rc=0
    meta=$(curl -s -o "$out" -w '%{http_code} %{content_type}' "$url") || rc=$?
    [ "$rc" = "0" ] || fail "$label could not be fetched from $BASE (curl exited $rc)"
    code=${meta%% *}
    media=${meta#* }
    media=${media%%;*}
    if [ "$code" != "200" ] || [ "$media" != "$want" ]; then
        show_response_head "$out"
        fail "$label returned HTTP $code as '$media', wanted 200 as '$want'"
    fi
}

show_response_head() {
    echo "--- first 400 bytes of the response ---"
    head -c 400 "$1"
    echo
}

# One value summarising a result set: the row count, how many geometries decode to a non-empty
# shape, and the extent they cover, longitude range first. A row count alone would pass on a
# result full of unreadable geometry, hence the spatial extension. Both output formats must
# report this identically.
SUMMARY_SELECT="select count(*) || '/' || count(*) filter (where not ST_IsEmpty(geom))
                     || '/' || round(ST_XMin(ST_Extent_Agg(geom)))::INT
                     || '/' || round(ST_XMax(ST_Extent_Agg(geom)))::INT
                     || '/' || round(ST_YMin(ST_Extent_Agg(geom)))::INT
                     || '/' || round(ST_YMax(ST_Extent_Agg(geom)))::INT from"
WORLD="$COUNTRIES/$COUNTRIES/-180/180/-90/84"
