# GeoServer 3.0 with the plugins of this repository, on Java 25 with --enable-preview.
#
# Stock GeoServer images run Java 17/21 and cannot load the parquetry classes (they carry
# preview bytecode). This image takes the official platform-independent GeoServer binary
# (self-contained Jetty + exploded webapp), drops the plugins into WEB-INF/lib the standard
# GeoServer way, and runs it on a Java 25 JVM.
#
# The plugin zips (build/plugin-<module>.zip) are produced on the host by the Makefile
# (./mvnw -Passembly package on each plugin module); this image only unzips them in place.
# One reactor build produces them all at one version: the shared jars (e.g.
# tileverse-geoserver-storage-web) are identical files, and unzipping a zip on top of another
# overwrites them harmlessly.

FROM eclipse-temurin:25

ARG GEOSERVER_VERSION=3.0.2

# Proven Java 25 runtime options from GeoServer Cloud's images. Beyond parquetry's own
# --enable-preview / --enable-native-access, GeoServer needs the java.desktop, java.naming
# and java.net.http opens. UseSVE=0 works around an Apple-M4 + Docker-on-macOS SIGILL;
# UseAVX=2 keeps any AOT cache portable across amd64 hosts.
ENV DEFAULT_JAVA_TOOL_OPTIONS="\
--enable-preview \
--add-opens=java.base/java.lang=ALL-UNNAMED \
--add-opens=java.base/java.util=ALL-UNNAMED \
--add-opens=java.base/java.lang.reflect=ALL-UNNAMED \
--add-opens=java.base/java.text=ALL-UNNAMED \
--add-opens=java.desktop/java.awt.font=ALL-UNNAMED \
--add-opens=java.desktop/sun.awt.image=ALL-UNNAMED \
--add-opens=java.desktop/sun.java2d.pipe=ALL-UNNAMED \
--add-opens=java.naming/com.sun.jndi.ldap=ALL-UNNAMED \
--add-opens=java.net.http/jdk.internal.net.http=ALL-UNNAMED \
--enable-native-access=ALL-UNNAMED \
-Djava.awt.headless=true \
-XX:MaxRAMPercentage=80 \
-XX:InitialRAMPercentage=30 \
-XX:+UseCompressedOops \
-XX:+UseCompactObjectHeaders \
-XX:+UseStringDeduplication \
-XX:+IgnoreUnrecognizedVMOptions \
-XX:UseSVE=0 \
-XX:UseAVX=2"
ENV JAVA_TOOL_OPTIONS="${DEFAULT_JAVA_TOOL_OPTIONS}"

RUN apt-get update \
    && apt-get install -y --no-install-recommends unzip curl \
    && rm -rf /var/lib/apt/lists/*

# The platform-independent binary unzips with no top-level directory; unpack it straight
# into /opt/geoserver (bin/, webapps/geoserver/, data_dir/, start.jar, ...).
#
# The bundled marlin-*.jar is dropped: startup.sh would inject it via --patch-module java.desktop,
# but this old Marlin references sun.security.action.GetPropertyAction, removed in JDK 24+ with the
# Security Manager, which breaks all WMS rendering. With no marlin jar, startup.sh falls back to the
# JDK's own (Java-25-compatible) Marlin renderer.
RUN curl -fSL --retry 4 --retry-delay 5 --retry-all-errors -o /tmp/geoserver.zip \
      "https://downloads.sourceforge.net/project/geoserver/GeoServer/${GEOSERVER_VERSION}/geoserver-${GEOSERVER_VERSION}-bin.zip" \
    && unzip -q /tmp/geoserver.zip -d /opt/geoserver \
    && rm /tmp/geoserver.zip \
    && rm -f /opt/geoserver/webapps/geoserver/WEB-INF/lib/marlin-*.jar \
    && rm -rf /opt/geoserver/data_dir

# Install the plugins the standard GeoServer way: unzip their jars into the webapp's WEB-INF/lib.
COPY build/plugin-*.zip /tmp/plugins/
RUN set -e; \
    for zip in /tmp/plugins/*.zip; do \
      unzip -o -q "$zip" -d /opt/geoserver/webapps/geoserver/WEB-INF/lib; \
    done; \
    rm -rf /tmp/plugins

# GeoServer's community GeoParquet module, used by the stores of the geoparquet-community
# workspace. It reads through DuckDB, and DuckDB downloads its extensions on the first request to
# one of these stores. The store migration page moves the stores to the parquetry plugin.
# GeoServer releases no community modules; this is the nightly of the GeoServer series. Build with
# --build-arg COMMUNITY_GEOPARQUET_MODULES= to leave the module out. A nightly zip may bundle
# newer copies of libraries already shipped with the webapp. Only the jars missing from the
# webapp are copied, keeping each library at one version.
#
# The zip names include the snapshot version of the series branch, and that version changes at
# each release: 3.0.3-SNAPSHOT after 3.0.2. The build reads the current names from the listing of
# the nightly directory.
ARG COMMUNITY_NIGHTLY_URL=https://build.geoserver.org/geoserver/3.0.x/community-latest
ARG COMMUNITY_GEOPARQUET_MODULES="geoparquet"
RUN set -e; \
    modules="$COMMUNITY_GEOPARQUET_MODULES"; \
    if [ -n "$(echo $modules)" ]; then \
      listing=$(curl -fsSL --retry 4 --retry-delay 5 --retry-all-errors "$COMMUNITY_NIGHTLY_URL/"); \
    fi; \
    for module in $modules; do \
      zip=$(printf '%s\n' "$listing" | grep -o "geoserver-[0-9.]*-SNAPSHOT-$module-plugin\.zip" | head -n 1); \
      if [ -z "$zip" ]; then \
        echo "no $module zip in $COMMUNITY_NIGHTLY_URL" >&2; \
        exit 1; \
      fi; \
      curl -fSL --retry 4 --retry-delay 5 --retry-all-errors -o /tmp/$module.zip \
        "$COMMUNITY_NIGHTLY_URL/$zip"; \
      mkdir /tmp/$module; \
      unzip -q /tmp/$module.zip -d /tmp/$module; \
      for jar in /tmp/$module/*.jar; do \
        base=$(basename "$jar" | sed -E 's/-[0-9]+(\.[0-9A-Za-z-]+)*\.jar$//'); \
        if ls /opt/geoserver/webapps/geoserver/WEB-INF/lib/"$base"-[0-9]*.jar >/dev/null 2>&1; then \
          echo "$module: keeping the webapp's own $base"; \
        else \
          cp "$jar" /opt/geoserver/webapps/geoserver/WEB-INF/lib/; \
        fi; \
      done; \
      rm -rf /tmp/$module /tmp/$module.zip; \
    done

# Bake the preconfigured data directory (workspaces + stores + published layers) and the sample
# datasets. The container then serves live layers on first boot with no manual steps.
COPY geoserver-data /opt/geoserver/data_dir
COPY data /data

ENV GEOSERVER_DATA_DIR=/opt/geoserver/data_dir
EXPOSE 8080
WORKDIR /opt/geoserver

ENTRYPOINT ["/opt/geoserver/bin/startup.sh"]
