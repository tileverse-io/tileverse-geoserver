#!/usr/bin/env bash
# Writes the Parquet fixtures of ParquetFileColumnsTest. Needs GDAL with its Parquet driver.
set -euo pipefail
cd "$(dirname "$0")"
trap 'rm -f points.csv table.csv' EXIT
command -v ogr2ogr >/dev/null || { echo "gdal required" >&2; exit 1; }
printf 'id,name,WKT\n1,a,"POINT (1 2)"\n2,b,"POINT (3 4)"\n3,c,"POINT (5 6)"\n' > points.csv
printf 'id,name\n1,a\n2,b\n' > table.csv
# three points in two row groups, with an EPSG:3857 CRS in their GeoParquet metadata
ogr2ogr -q -f Parquet -a_srs EPSG:3857 -oo GEOM_POSSIBLE_NAMES=WKT -oo KEEP_GEOM_COLUMNS=NO \
  -lco ROW_GROUP_SIZE=2 -lco WRITE_COVERING_BBOX=NO points_3857.parquet points.csv
# the same points in longitude and latitude: GeoParquet then leaves the CRS out, meaning OGC:CRS84
ogr2ogr -q -f Parquet -a_srs EPSG:4326 -oo GEOM_POSSIBLE_NAMES=WKT -oo KEEP_GEOM_COLUMNS=NO \
  -lco ROW_GROUP_SIZE=2 -lco WRITE_COVERING_BBOX=NO points_4326.parquet points.csv
# the same points with EPSG:3857 in the native Parquet geometry type only, without GeoParquet metadata
# (the native types need libarrow 21 or later)
ogr2ogr -q -f Parquet -a_srs EPSG:3857 -oo GEOM_POSSIBLE_NAMES=WKT -oo KEEP_GEOM_COLUMNS=NO \
  -lco ROW_GROUP_SIZE=2 -lco WRITE_COVERING_BBOX=NO -lco USE_PARQUET_GEO_TYPES=ONLY \
  points_3857_native.parquet points.csv
# the same points with the native geometry type and the GeoParquet metadata together (needs libarrow 21 or later)
ogr2ogr -q -f Parquet -a_srs EPSG:3857 -oo GEOM_POSSIBLE_NAMES=WKT -oo KEEP_GEOM_COLUMNS=NO \
  -lco ROW_GROUP_SIZE=2 -lco WRITE_COVERING_BBOX=NO -lco USE_PARQUET_GEO_TYPES=YES \
  points_3857_both.parquet points.csv
# a plain Parquet table, without geometry
ogr2ogr -q -f Parquet table.parquet table.csv
ls -la ./*.parquet
