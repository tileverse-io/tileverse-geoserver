# tileverse-geoserver-parquetry

A thin GeoServer plugin that registers the read-only GeoParquet `DataStore`
(from `parquetry-geotools`) as a vector store type in the GeoServer UI and REST
configuration.

The data-access code lives in `parquetry-geotools`, and GeoServer auto-discovers
its `DataStoreFactorySpi` from that jar. This module adds the Spring wiring and a
custom store-edit panel. The `applicationContext.xml` here declares:

- a `DataStorePanelInfo` binding `GeoParquetDataStoreFactory` to the custom
  `GeoParquetDataStoreEditPanel` (see below),
- a `ModuleStatusImpl` per feature: each store type and each WFS output format
  reports its own row under About > Server Status > Modules,
- a `GeoServerLifecycleHandler` clearing the caches of parquetry (footers,
  learned page indexes, byte ranges) when GeoServer resets or reloads, and
- a `GeoParquetMigrationConfiguration` adding the store migration page to the
  Tools menu, with the migration of the community GeoParquet stores (see
  [Migrating from the community GeoParquet store](#migrating-from-the-community-geoparquet-store)).

## Cloud storage panel

`GeoParquetDataStoreEditPanel`, `IcebergDataStoreEditPanel` and
`StacDataStoreEditPanel` render the tileverse `storage.*` connection parameters
through the shared storage section from [storage-web](../storage-web): a
`storage.provider` selector as a segmented toggle (GeoParquet and Iceberg) or
one checkbox per backend (STAC), then each backend's fields under a header, in
the provider's declared order, showing only the selected backends. Booleans
render as checkboxes, the AWS region as a searchable dropdown, retry delays as
duration widgets, and secrets (keys, tokens, passwords) are masked. For a
remote backend, the memory-cache toggle comes first, ticked: the parquetry
stores cache the byte ranges read from remote storage unless a store turns the
cache off. Local files have no byte-range cache.

With no provider selected, the URL scheme selects the backend: `s3://`,
`gs://`, `az://`, `abfs(s)://`, a local path, and plain HTTP for `http(s)://`.
Selecting a provider overrides that, for example to read an `https://` URL
through the S3 backend. A store saved without a provider, over REST for
instance, opens with the backend of its URL selected.

A store keeps the parameters of its selected backends only. The panel stores
their declared defaults as soon as their fields show (S3 path-style access on,
for example), keeping the store stable across a change of an engine default.
A default declared by the store wins over the one declared by tileverse: the
memory cache starts ticked. Saving the store drops the parameters of the other
backends. With no backend selected, no backend parameter is kept.

## Browsing for the location

The location field of each store has a "Browse..." button. It opens a dialog
over the storage named by the typed URL and storage parameters, and does not
save the store. The dialog lists one folder at a time, with the name, file size
and date of each entry. For a `.parquet` file it also shows the rows, row
groups, primary geometry column, CRS and GeoParquet version. The geometry column
shows its native Parquet type, `Geometry` or `Geography`, when it has one, and
the encoding named by the GeoParquet metadata otherwise. GeoParquet picks a
file or a folder, Iceberg a table or warehouse folder, and STAC a catalog
document or an items table. Local files follow GeoServer's file sandbox and
`GEOSERVER_FILEBROWSER_HIDEFS`, as GeoServer's own file chooser does, and a
local choice is written as an absolute `file:` URI.

## Migrating from the community GeoParquet store

The GeoServer community GeoParquet module reads GeoParquet files through
DuckDB. The plugin can move the stores of that module to the Parquetry store,
in place. A migrated store keeps its id, its layers and their styles. The
migration rewrites its type and connection parameters, and keeps a backup of
the original definition. A migration needs no restart. From then on, the store
opens in the Parquetry store page.

Administrators find the page under Tools, entry "Store migration". Its title
is "Migrate community stores". The table lists the stores of the community
module, and the migrated stores with a backup. The status of a store links to
its details. For a checked store, the details show the parameters before and
after, the findings of the last check, and the native names of the layers
before and after. For a migrated store, they show its current parameters. The
details mask secrets as `********`.

The buttons act on the selected stores:

- **Check** translates the parameters of a store, opens the result as a
  Parquetry store without saving it, and compares its layers with the
  published ones. A check ends "Ready", "Ready with differences" or
  "Blocked", or "Failed" when it cannot run.
- **Migrate** acts on the stores checked as "Ready" or "Ready with
  differences", and asks for confirmation when a store has differences. It
  checks the store again before writing it. If the new check is worse than
  the one shown, or finds a difference missing from the one shown, the store
  stays as it is and the page shows the new check.
- **Revert** restores the original definition of a migrated store and removes
  its backup. Edits made to the store after the migration are lost. The
  reverted store needs the community module again.
- **Discard backup** removes the backup and makes the migration final. The
  store leaves the list.

Checks, migrations and reverts run in the background. The page refreshes until
they end.

The backup of the original store is kept in the metadata of the store. It
holds the original `uri` with its secrets. GeoServer encrypts the backup with
the password encryption configured for store passwords. When that encryption
is off, the backup is stored in plain text, like the store passwords.

When a migration or a revert fails, the status of the store says so, and its
details show the error. The GeoServer log has a warning naming the store.

### Translated parameters

The community store takes its S3 settings from the query string of its `uri`
and from its own parameters. The Parquetry store takes them as `storage.s3.*`
parameters:

| Community store                                                | Parquetry store                            |
|----------------------------------------------------------------|--------------------------------------------|
| `uri`, without its query string                                | `geoparquet`                               |
| `region` or `s3_region` in the URI, else `aws_region`          | `storage.s3.region`                        |
| `access_key` or `s3_access_key_id` in the URI                  | `storage.s3.aws-access-key-id`             |
| `secret_key` or `s3_secret_access_key` in the URI              | `storage.s3.aws-secret-access-key`         |
| `endpoint` or `s3_endpoint` in the URI, else `endpoint`        | `storage.s3.endpoint`                      |
| `url_style` or `s3_url_style` in the URI, else `url_style`     | `storage.s3.force-path-style` when `path`  |
| `s3_use_ssl` in the URI                                        | the scheme of `storage.s3.endpoint`        |
| `aws_profile`                                                  | `storage.s3.default-credentials-profile`   |
| `namespace`                                                    | `namespace`                                |
| `primary_key_id`                                               | `fid`                                      |

A setting in the URI wins over the store parameter of the same meaning. An
endpoint without a scheme gets `http://` in front when `s3_use_ssl` is
`false` in the URI, and `https://` otherwise, with a note. An endpoint with
a scheme stays as written. DuckDB reads
an `s3://` location anonymously when the URI has no access key and
`use_aws_credential_chain` is off. The migrated store does the same: the
migration sets `storage.s3.anonymous` to `true`, with a note.

The S3 secret key moves from the URI into
`storage.s3.aws-secret-access-key`, a password parameter. GeoServer encrypts
it like the other store passwords.

The Parquetry store has no counterpart for the DuckDB memory limit
(`memory_limit`) and thread count (`threads`), `fetch size` and
`screenmap`. The migration leaves them out, together with other parameters
unknown to it, and lists them in one note.

Two settings in the URI block the migration:

- a setting in the URI query unknown to the migration, such as a misspelled
  key;
- a session token in the URI (`session_token` or `s3_session_token`). The
  migration does not translate session tokens.

A store without a `uri` is blocked too.

### Layers

A `uri` naming a single `.parquet` file gives one layer. For a folder or a
glob, the check tries the layer groupings offered by the installed Parquetry,
in this order. It keeps the first grouping with a layer for each published
layer:

1. `file`: one layer per top-level `.parquet` file;
2. `partition`: one layer per Hive partition, down to the `max_hive_depth`
   of the community store when it has one;
3. `merged`: the files of the folder as one layer.

The community store reads a Hive tree through a recursive glob, such as
`<root>/**/*.parquet`. The check tries the `uri` as written first. When it
has a `**` segment, the check then tries the folder above the first glob
segment, `<root>/`, with the same groupings. Parquetry 1.0-RC6 finds no
files through a recursive glob, and can read the folder instead. When the
folder wins, the migrated store reads the folder, with a note.

Parquetry 1.0-RC6 offers `file` and `merged` only. A store publishing a
layer per Hive partition is blocked, with the note "Layers per Hive partition
need a newer Parquetry". A Hive tree read with `max_hive_depth` set to 0 is
one layer, and migrates as the `merged` layer of its folder.

A check is also blocked when the translated store cannot be opened, or when a
published layer has no counterpart in it.

A layer keeps its name, its id and its styles. Its native name changes when
Parquetry names the file differently from the community store. For example,
the community store publishes `my-points.parquet` as `my_points`, and
Parquetry as `my-points`.

The community store appends the query string of its `uri` to the layer
names. With the `uri` `s3://naturalearth/*.parquet?s3_use_ssl=false`, it
publishes `countries.parquet` as `countries.parquet_s3_use_ssl_false`, and
Parquetry as `countries`. The check matches such a layer to the layer of its
file, and the migration renames its native name.

### Checks without the community module

With the community module installed, the check reads the attributes of the
layers from the community store. Without it, the check compares the attributes
saved in the catalog. An attribute saved without a type is compared by name
only. When a layer has no saved attributes, the check ends "Ready with
differences", with the note "Attributes not compared: the community module is
not installed". With or without the module, the check compares the CRS found
by Parquetry with the native CRS saved with the layer.

### What changes for clients

A migrated layer serves the same features, with these differences. Because
the feature ids change, a check of a store with published layers ends "Ready
with differences" at best.

- Geometry attributes are described as `Geometry` instead of their specific
  type, such as `Point`.
- The `id` column of a file becomes a visible attribute. The community store
  hid it as its key.
- Covering columns such as `bbox` hold the bounding box of each geometry.
  Parquetry reads them to filter rows and does not publish them. A layer
  with saved attributes reading one cannot migrate until that attribute is
  removed.
- Struct columns read as maps.
- Timestamps and some integer columns change their Java type.
- Feature ids change. The community store prefixes them with the layer name,
  as in `points.1`, and Parquetry does not, as in `1`. A request by a
  feature id served before does not find its feature. For a file without an
  id column and without a feature id column, such a request fails.
- With a narrow glob and one layer per file, such as `<dir>/points_*.parquet`,
  the Parquetry store exposes each file of the folder. The layers not
  published before stay unpublished.

An administrator may customize the attributes of a layer. GeoServer then
saves the attribute list with the layer, and reads each attribute from the
store. The check blocks such a layer when Parquetry lacks one of its
attributes, or a property read by the source expression of one. Otherwise the
layer would stop serving after the migration.

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
`LICENSE` file and the third-party attribution is in `NOTICE`, both at the
repository root and baked into the module jar's `META-INF` at build time. The
rest of parquetry is licensed under the Apache License, Version 2.0.
