#!/usr/bin/env bash
#
# Smoke check for the demo's core services, against a running demo (`make demo-up`): REST
# answers authenticated, WFS counts every country in both workspaces, and WMS renders the
# world layer group. Needs no DuckDB.

set -euo pipefail

SMOKE_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=demo/smoke/lib.sh
. "$SMOKE_DIR/lib.sh"

CREDENTIALS=${GEOSERVER_CREDENTIALS:-admin:geoserver}

require_geoserver

code=$(curl -s -o /dev/null -w '%{http_code}' -u "$CREDENTIALS" "$BASE/rest/about/version.json")
[ "$code" = "200" ] || fail "REST about/version returned HTTP $code"
echo "REST about/version: 200"

for ws in "${WORKSPACES[@]}"; do
    hits=$(curl -s "$(getfeature_url "$ws")&resultType=hits" \
        | grep -oE 'numberMatched="[0-9]+"' | grep -oE '[0-9]+' || true)
    echo "$ws:countries numberMatched=${hits:-<none>}"
    [ "$hits" = "$COUNTRIES" ] || fail "$ws:countries expected $COUNTRIES, got '${hits:-<none>}'"
done

for ws in "${WORKSPACES[@]}"; do
    out=/tmp/$ws-world.png
    curl -s -o "$out" "$BASE/$ws/wms?service=WMS&version=1.1.1&request=GetMap&layers=$ws:world\
&bbox=-180,-90,180,90&width=400&height=200&srs=EPSG:4326&format=image/png" \
        || fail "$ws:world GetMap could not be fetched from $BASE"
    kind=$(file -b "$out")
    echo "$ws:world -> $kind"
    case "$kind" in
        "PNG image data"*) ;;
        *) fail "$ws:world did not render a PNG ($kind)" ;;
    esac
done

echo "Core service checks passed."
