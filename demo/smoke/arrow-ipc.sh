#!/usr/bin/env bash
#
# Smoke check for the Arrow IPC WFS output format, against a running demo (`make demo-up`).
#
# arrow-ipc is a stream format and this check consumes it as one: the response goes straight
# from curl into DuckDB through /dev/stdin, never landing as a file. tee keeps a copy only to
# quote the body when something other than Arrow comes back.

set -euo pipefail

SMOKE_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=demo/smoke/lib.sh
. "$SMOKE_DIR/lib.sh"

require_duckdb
require_geoserver

ARROW_MEDIA_TYPE=application/vnd.apache.arrow.stream

for ws in "${WORKSPACES[@]}"; do
    headers=/tmp/$ws-arrow-headers.txt
    body=/tmp/$ws-arrow-body
    rc=0
    got=$(curl -s -D "$headers" "$(getfeature_url "$ws")&outputFormat=arrow-ipc" \
        | tee "$body" \
        | duckdb -noheader -list -c "load arrow; load spatial; $SUMMARY_SELECT read_arrow('/dev/stdin')" 2>&1) || rc=$?

    # Absent header file or missing Content-Type must not abort the script under pipefail:
    # an empty value falls through to the mismatch report below.
    status=$(head -1 "$headers" 2>/dev/null | tr -d '\r' | awk '{print $2}' || true)
    media=$(grep -i '^content-type:' "$headers" 2>/dev/null | tail -1 | tr -d '\r' | sed 's/.*: *//; s/;.*//' || true)
    if [ "$status" != "200" ] || [ "$media" != "$ARROW_MEDIA_TYPE" ]; then
        show_response_head "$body"
        fail "$ws arrow-ipc returned HTTP $status as '$media', wanted 200 as '$ARROW_MEDIA_TYPE'"
    fi
    if [ "$rc" != "0" ]; then
        show_response_head "$body"
        fail "$ws arrow-ipc stream could not be read: $got"
    fi

    got=$(printf '%s\n' "$got" | tail -1)
    echo "$ws:countries as arrow-ipc -> $got"
    [ "$got" = "$WORLD" ] || fail "$ws arrow-ipc summarised as '$got', wanted '$WORLD'"
done

echo "Arrow IPC output format checks passed."
