# GitHub Actions workflows

CI and publishing for tileverse-geoserver. All builds run on **Java 25**
(`temurin`). Preview features (`--enable-preview`) are enabled by the compiler
and test plugins in the POM; `.mvn/jvm.config` adds the native-access flags
`./mvnw` needs. GeoServer and GeoTools resolve from the OSGeo repository
(configured in the plugin POM); the parquetry engine resolves from Maven Central
(releases) or the Central snapshot repository (see the root POM).

## `pr-validation.yml`

Gates every pull request and every push to `main` (ignoring `docs/**` and
`**.md`). Three jobs:

- **lint** (ubuntu): `make lint` - Spotless, SortPOM, and license-header checks.
- **build** (ubuntu): `./mvnw verify -Pcoverage` (unit + integration tests),
  uploading the JaCoCo report and the surefire/failsafe reports.
- **pr-validation-complete**: the single required status check. Mark this job as
  the required check in branch protection.

Needs no secrets.

## `geoserver-demo-smoke.yml`

Builds the GeoServer GeoParquet plugin, installs it into an official GeoServer
binary the standard way (unzip into `WEB-INF/lib`), starts the demo stack
(GeoServer + the s3proxy emulator) with `make geoserver-demo`, and exercises
both workspaces over REST, WFS and WMS - including the `parquetry-s3` layers
that read from S3 through the AWS default credential chain. Runs on every PR and
push to `main` (ignoring `docs/**` and `**.md`). Needs no secrets.

## `publish-snapshot.yml`

After `Pull Request Validation` completes successfully on `main` (or on manual
dispatch), deploys the `1.0-SNAPSHOT` build to the Central snapshot repository.
Skipped when the head commit message contains `[skip-publish]`.

## `publish-release.yml`

Builds, tests, and publishes a release to Maven Central, then creates the GitHub
Release. The version is fed to Maven as `-Drevision=<version>` (the POM uses
CI-friendly `${revision}` versioning; the tag drives the published version). The
GitHub Release also gets two attachments built at the same version: the
GeoServer plugin zip (`tileverse-geoserver-parquetry-<version>-plugin.zip`) and the
self-contained demo zip (`tileverse-geoserver-parquetry-demo-<version>.zip`).

Triggered by either:

- **Pushing a tag whose name starts with a digit** - `1.0-M1`, `1.0.0`,
  `2.0-RC1`. The version is the tag name verbatim. Tags that do not start with
  a digit are ignored and never trigger a publish.
- **`workflow_dispatch`** with an explicit `version` input - for releasing any
  ref or version by hand.

## Required repository secrets

Only the two publish workflows need these:

| Secret | Purpose |
| --- | --- |
| `GPG_PRIVATE_KEY` | Armored private key used to sign artifacts. |
| `GPG_PASSPHRASE` | Passphrase for that key. |
| `CENTRAL_USERNAME` | Central Portal user token name. |
| `CENTRAL_TOKEN` | Central Portal user token secret. |
