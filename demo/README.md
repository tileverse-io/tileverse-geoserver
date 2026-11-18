# Tileverse GeoServer plugins - demo

A one-command demo that serves [Natural Earth](https://www.naturalearthdata.com/) as live WMS
and WFS layers in [GeoServer](https://geoserver.org/), reading from
[GeoParquet](https://geoparquet.org/) files through the parquetry GeoParquet plugin. No GeoServer
install, no manual configuration.

## What it is

A self-contained Docker image: GeoServer 3.0 plus the parquetry GeoParquet plugin, on Java 25,
plus a bundled `s3proxy` S3 emulator. Two workspaces (mirroring GeoServer's stock `ne` workspace,
but reading GeoParquet) are baked in. Working layers are served the moment the stack boots:

| layer | geometry | features | default style |
|---|---|---|---|
| `countries` | polygons | 242 | countries_mapcolor9 |
| `boundary_lines` | lines | 390 | boundary_lines |
| `coastlines` | lines | 1428 | coastline |
| `disputed_areas` | polygons | 28 | disputed |
| `populated_places` | points | 1251 | populated_places |

Each appears in two workspaces, plus a `world` layer group ("World Map") in each:

- **`parquetry`** - reads all five layers from a single GeoParquet directory store
  (`layer-grouping=file`, one layer per file) on the local filesystem (baked into the image).
- **`parquetry-s3`** - reads the same data over S3 from the bundled `s3proxy` emulator, again as a
  single directory store. The store has no credentials; GeoServer uses the AWS default credential
  chain (`secrets/aws/credentials` mounted as `~/.aws`), and s3proxy serves `data/ne` as the bucket
  `naturalearth` (a bind mount, no upload). This mirrors a real deployment reading from S3 with an
  instance role or environment credentials.

A third workspace holds stores of GeoServer's community GeoParquet module, to try the store
migration page of the parquetry plugin (Tools, entry "Store migration"):

- **`geoparquet-community`** - the same Natural Earth files, read by the community module through
  DuckDB:
  - the `ne` store reads the local directory and publishes `countries` and `populated_places`;
  - the `ne-s3` store reads the bucket `naturalearth` on s3proxy with the AWS default credential
    chain, and publishes `coastlines` and `boundary_lines_land`. Its `uri` ends in
    `?s3_use_ssl=false` because s3proxy speaks plain HTTP. The community module appends that
    query string to the names of its feature types, such as `coastlines.parquet_s3_use_ssl_false`.

  DuckDB downloads its extensions on the first request to one of these stores, and needs internet
  access for it.

## Prerequisites

- Docker (with Compose)
- To rebuild the plugin from source: a JDK 25 (the bundled `./mvnw` is used; nothing else)

## Run it

The `Makefile` is one level up, at the repository root. From there:

```bash
make demo-up
```

That builds the plugin zips, builds the image, and starts GeoServer. Then open:

- Web admin: <http://localhost:8080/geoserver>  (user `admin`, password `geoserver`)
- Layer preview: Web admin -> Layer Preview -> `parquetry:world`

Stop it with `make demo-down`. Follow startup logs with `make demo-logs`. If the image is
already built, `docker compose up` from this directory is enough.

The GeoServer configuration lives in a named Docker volume, seeded from the baked-in data
directory on first start and kept across `make demo-down` and `make demo-up`. Stores, layers and
styles added through the web admin or the REST API survive a restart and an image rebuild: a
plugin rebuilt from source picks up the same configuration. `make demo-clean` removes the volume
along with the image; that is also the only way newly baked layers show up in an existing demo.
To turn configuration made through the web admin into demo defaults, `make demo-export` copies
the workspaces, styles and layer groups of the running demo back into `demo/geoserver-data`,
ready to review and commit.

## Build a customer zip

```bash
make demo-dist
```

produces `target/tileverse-geoserver-demo.zip`: a self-contained bundle (Dockerfile, the prebuilt
plugin zips, the preconfigured data directory and the data, and a compose file). The recipient
needs only Docker - they unzip it and run `docker compose up`, with no checkout of this
repository and no image registry. The first `up` builds the image locally.

## Try the services

WMS GetMap (the world map):

```bash
curl -o world.png \
  "http://localhost:8080/geoserver/parquetry/wms?service=WMS&version=1.1.1&request=GetMap\
&layers=parquetry:world&bbox=-180,-90,180,90&width=1000&height=500&srs=EPSG:4326&format=image/png"
```

WFS GetFeature (GeoJSON):

```bash
curl "http://localhost:8080/geoserver/parquetry/wfs?service=WFS&version=2.0.0&request=GetFeature\
&typeNames=parquetry:populated_places&count=5&outputFormat=application/json"
```

## Smoke checks

`demo/smoke/` holds the checks CI runs against the demo, as scripts you can run yourself once
`make demo-up` is healthy. `make demo-smoke` runs `core.sh` first, then the other scripts, one or
more per plugin:

```bash
make demo-smoke
```

`core.sh` covers REST, WFS counts and WMS rendering in both workspaces.
`geoparquet-community.sh` counts the features of the four layers of the community GeoParquet
module. `geoparquet.sh` and `arrow-ipc.sh` download the countries layer in each output format and
read it back with DuckDB, which they expect on `PATH`. Run one on its own, or point any of them at
another instance:

```bash
demo/smoke/arrow-ipc.sh
GEOSERVER_URL=http://gs.example.com/geoserver demo/smoke/geoparquet.sh
```

## The data

The GeoParquet files in `data/ne/` are converted from the Natural Earth GeoPackage that ships
with the GeoServer release (`data/release/data/ne/natural_earth.gpkg`) using GDAL. Regenerate them
with `data/generate.sh` (see that script for the source path and conversion options). To use your
own data, drop GeoParquet files in `data/ne/` and adjust the `ne` datastore under
`geoserver-data/workspaces/parquetry/`.

## How it is built

- `make plugins` runs `./mvnw -Passembly package` on the plugin modules, producing the standard
  GeoServer-plugin zip of each (the plugin jar plus its runtime dependencies; libraries GeoServer
  already ships are excluded). The demo targets stage them as `build/plugin-<module>.zip`.
- The `Dockerfile` downloads the official GeoServer 3.0 platform-independent binary, drops the old
  bundled Marlin renderer (incompatible with JDK 24+), unzips the plugins into `WEB-INF/lib`, and
  bakes in the preconfigured workspaces and their sample data.
- It also installs GeoServer's community GeoParquet module (`geoparquet`) from the GeoServer
  series nightly build, as community modules have no releases. The layers of the
  `geoparquet-community` workspace need it. Build with
  `--build-arg COMMUNITY_GEOPARQUET_MODULES=` to leave it out.
- GeoServer runs on Java 25 with the runtime options proven by GeoServer Cloud
  (`--enable-preview`, the required `--add-opens`, and the Apple-M4/Docker SVE workaround).
- GeoServer regenerates the rest of its data directory (services, default styles, security) on
  first boot; only the demo workspaces are committed here.

## Notes

- Credentials are the GeoServer defaults (`admin` / `geoserver`); change them for anything beyond
  a local demo.
- This serves GeoParquet from local files. Reading from object storage (S3/Azure/GCS/HTTP) through
  the same plugin is supported via the datastore's `storage.*` parameters; an opt-in cloud profile
  (S3 via s3proxy, no upload step) is planned for this demo.
