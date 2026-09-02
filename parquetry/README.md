# tileverse-geoserver-parquetry

A thin GeoServer plugin that registers the read-only GeoParquet `DataStore`
(from `parquetry-geotools`) as a vector store type in the GeoServer UI and REST
configuration.

The data-access code lives in `parquetry-geotools`, and GeoServer auto-discovers
its `DataStoreFactorySpi` from that jar. This module adds the Spring wiring and a
custom store-edit panel. The `applicationContext.xml` here declares:

- a `DataStorePanelInfo` binding `GeoParquetDataStoreFactory` to the custom
  `GeoParquetDataStoreEditPanel` (see below), and
- a `ModuleStatusImpl` so the plugin appears under About > Server Status >
  Modules.

## Cloud storage panel

`GeoParquetDataStoreEditPanel` is a provider-driven store-edit panel. The factory
declares the tileverse `storage.*` connection parameters (S3, Azure, GCS, HTTP
auth plus a memory-cache toggle); the panel renders a `storage.provider` selector
as a segmented toggle and reveals only the selected provider's fields, hiding the
rest. The AWS region is a searchable Select2 dropdown, and secret fields (keys,
tokens, passwords) are masked. The provider is auto-detected from the URI when
left unset. Local files need no provider selection.

## WFS output formats

The plugin adds two WFS `GetFeature` output formats, listed under
`<outputFormat>` in the WFS 1.0, 1.1, and 2.0 capabilities documents:

| Format     | `outputFormat` value | MIME type alias                       | Attachment extension |
|------------|-----------------------|----------------------------------------|-----------------------|
| GeoParquet | `geoparquet`          | `application/vnd.apache.parquet`       | `.parquet`             |
| Arrow IPC  | `arrow-ipc`           | `application/vnd.apache.arrow.stream`  | `.arrows`              |

Either the format name or the MIME type works as `outputFormat`. The response
is attached as `<layer>.<extension>` (the requested type name); a `FILENAME`
format_option, honored by GeoServer's base `GetFeature` output format,
overrides the attachment name.

Both formats serve a single query per request. A `GetFeature` request naming
more than one `typeName`, or one whose result holds complex features, fails
with a WFS exception instead of a partial response.

GeoParquet reads three write knobs from `format_options`, matched
case-insensitively; any knob left unset keeps the write engine's default.

| Option          | Values                                          |
|------------------|--------------------------------------------------|
| `parquetVersion` | `1.1`, `2.0`                                      |
| `rowGroupSize`   | a positive row count                              |
| `compression`    | `zstd`, `snappy`, `gzip`, `lz4`, `none`           |

Arrow IPC takes no `format_options`; its output comes entirely from the
engine's fixed defaults.

```bash
curl -s "http://localhost:8080/geoserver/ows?service=WFS&version=2.0.0&request=GetFeature&typeNames=topp:states&outputFormat=geoparquet" -o states.parquet
curl -s "http://localhost:8080/geoserver/ows?service=WFS&version=2.0.0&request=GetFeature&typeNames=topp:states&outputFormat=arrow-ipc&count=1000" -o states.arrows
```

## Runtime requirement

`parquetry-core` is Java 25 bytecode compiled with `--enable-preview`. The
plugin therefore loads only on a **Java 25 JVM started with `--enable-preview`**
(plus the foreign-memory native-access flags parquetry uses). A Java 17
GeoServer cannot load it. The deployment target is GeoServer Cloud on Java 25.

## Manual verification with `jetty:run`

The plugin is wired into the GeoServer web app through a `parquetry` Maven
profile in the GeoServer source tree (`src/web/app/pom.xml`).

1. Build and install parquetry locally:

   ```bash
   ./mvnw -pl :tileverse-geoserver-parquetry -am install
   ```

2. From the GeoServer checkout (branch with the `parquetry` profile), run the
   web app on a Java 25 JVM:

   ```bash
   cd src/web/app
   MAVEN_OPTS="--enable-preview --enable-native-access=ALL-UNNAMED" \
     mvn jetty:run -Pparquetry
   ```

   Match the JVM arguments to parquetry's `.mvn/jvm.config`.

3. In the GeoServer UI, go to Stores > Add new store. "Parquet" appears in
   the vector data sources. Create a store with a `uri` pointing at a
   GeoParquet file (local path or `s3://`, `gs://`, `https://`, etc., per the
   tileverse storage backends), then publish a layer from it.

## Run embedded from an IDE (`StartGeoServer`)

`src/test/java/io/tileverse/geoserver/parquetry/StartGeoServer.java` launches the
full GeoServer web app, with this plugin on the classpath, inside an embedded
Jetty. Run it from an IDE as a Java application (Run As > Java Application) for a
quick debug loop. The test-scope `gs-web-app` + Jetty 12.1.8 dependencies host it;
none of them ship with the published plugin.

A minimal `web.xml` is bundled under `src/test/resources/webapp`. **No GeoServer
source checkout is needed** - `StartGeoServer` serves the bundled webapp and
loads GeoServer plus the plugin from the classpath. To serve a different webapp
(for example a real GeoServer source tree), override it with
`-Dgeoserver.webapp=/path/to/webapp`.

Run configuration (the JVM flags match parquetry's `.mvn/jvm.config`):

```
VM arguments:
  --enable-preview --enable-native-access=ALL-UNNAMED
```

GeoServer comes up at <http://localhost:8080/geoserver> (override the port with
`-Djetty.port=...`); type `stop` in the console to shut down. Then add a
"Parquet" store as in step 3 above.

## License

This module is licensed under the **GNU General Public License, version 2 or
later** (`GPL-2.0-or-later`), because it is a GeoServer plugin and reuses
GeoServer's GPL-2.0-or-later Wicket components. The full license text is in the
`LICENSE` file and the third-party attribution is in `NOTICE`, both in this
directory and bundled into the module jar under `META-INF`. The rest of
parquetry is licensed under the Apache License, Version 2.0.
