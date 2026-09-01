# Tileverse GeoServer

GeoServer plugins for the [tileverse](https://github.com/tileverse-io/tileverse)
and [parquetry](https://github.com/tileverse-io/parquetry) data formats, plus the
shared UI module they build on. Today the repo ships the Parquetry plugin
(GeoParquet, Iceberg, and STAC vector stores); plugins for other formats
arrive as their engines mature.

| Module | Artifact | Java |
|---|---|---|
| `storage-web/` | `io.tileverse.geoserver:tileverse-geoserver-storage-web` | 17 |
| `parquetry/` | `io.tileverse.geoserver:tileverse-geoserver-parquetry` | 25 (preview) |

`storage-web` holds the tileverse-storage-aware Wicket store-edit panels
(provider selector, per-backend connection parameters, secret masking, the
searchable AWS region dropdown). It has no dependency on any format engine and
no preview bytecode; any store plugin that reads through tileverse-storage's
`RangeReader` SPI (local files, HTTP, S3, Azure, GCS) can reuse it.

The Parquetry plugin's data-access code lives in
[parquetry](https://github.com/tileverse-io/parquetry) (the `parquetry-geotools`
module); GeoServer auto-discovers its `DataStoreFactorySpi` from that jar. The
plugin adds the Spring wiring, the store-edit panels, and the packaging for the
GeoParquet, Iceberg, and STAC stores.

Parquetry is a clean-room, bounded-memory Parquet/GeoParquet reader with no
dependency on Hadoop or parquet-java, which suits GeoServer running in a pod with
a gigabyte or two of heap.

## Requirements

- **Java 25**, started with `--enable-preview` and the foreign-memory
  native-access flags, for the Parquetry plugin: `parquetry-core` is compiled
  with preview features, and a Java 17/21 GeoServer cannot load it. The
  `storage-web` module itself is plain Java 17.
- **GeoServer 3.0.0 / GeoTools 35.0** (the `provided` versions this repo builds
  against). The deployment target is GeoServer Cloud on Java 25.
- Maven (use the bundled `./mvnw`).

## Building the Parquetry plugin

The plugin zip is a flat set of jars to unzip into a GeoServer installation's
`webapps/geoserver/WEB-INF/lib`. It contains the plugin jar, the `storage-web`
jar, and the runtime dependencies GeoServer/GeoTools do not already ship.

```bash
# Build tileverse-geoserver-parquetry-<version>-plugin.zip under parquetry/target/
make geoserver-plugins

# Equivalent direct invocation
./mvnw -pl :tileverse-geoserver-parquetry -am -Passembly package
```

Install it by unzipping into an existing GeoServer, then restart:

```bash
unzip tileverse-geoserver-parquetry-<version>-plugin.zip -d "$GEOSERVER_HOME/webapps/geoserver/WEB-INF/lib"
```

In the GeoServer UI, go to **Stores > Add new store**; "Parquet", "Iceberg",
and "STAC" appear among the vector data sources. See
[parquetry/README.md](parquetry/README.md) for the store-edit panels,
embedded-Jetty development launcher, and `jetty:run` instructions.

## Demo

A self-contained Docker demo serves six workspaces (GeoParquet, Iceberg, and STAC,
each over local disk and over an S3 emulator):

```bash
make demo-up     # build the plugins, build the image, start GeoServer
# open http://localhost:8080/geoserver  (admin / geoserver)
make demo-down   # stop and remove the containers
```

`make demo-dist` bundles the demo into a customer zip that needs only Docker.

## Coordinates

Published under the `io.tileverse.geoserver` group. Versions track the
compatible GeoServer series: `3.1-SNAPSHOT` is the development build for
GeoServer 3.1, and releases number the series independently (`3.1-1`, `3.1-2`,
...) because the plugins may release more than once against one GeoServer
series. The `3.0.x` branch does the same for GeoServer 3.0.

```xml
<dependency>
  <groupId>io.tileverse.geoserver</groupId>
  <artifactId>tileverse-geoserver-parquetry</artifactId>
  <version>3.1-SNAPSHOT</version>
</dependency>
```

Releases before the repository was renamed to tileverse-geoserver published as
`io.tileverse.parquetry.geoserver:parquetry-geoserver-plugin` (last: `1.0-RC`);
those artifacts stay on Central, new versions use the coordinates above.

The parquetry engine resolves from Maven Central (releases) or the Central
snapshot repository (snapshots); GeoServer and GeoTools resolve from the OSGeo
repository. Both repositories are declared in the POMs.

## Releases

Pushing a tag whose name starts with a digit (`1.0-M1`, `1.0.0`, `2.0-RC1`)
builds, tests, and publishes that exact version of every module to Maven
Central, then creates the matching GitHub Release with the plugin zip and the
self-contained demo zip attached. Any other version is published by hand through
the release workflow's `workflow_dispatch`. SNAPSHOTs publish automatically from
`main` once PR validation passes. See [.github/workflows](.github/workflows/README.md).

## License

**GNU General Public License, version 2 or later** (`GPL-2.0-or-later`), because
these are GeoServer plugins and reuse GeoServer's GPL-2.0-or-later Wicket
components. The full text is in [LICENSE](LICENSE) and third-party attribution is
in [NOTICE](NOTICE). The parquetry engine and the tileverse libraries they depend
on are licensed separately under the Apache License, Version 2.0.

Part of [Tileverse](https://tileverse.io).
