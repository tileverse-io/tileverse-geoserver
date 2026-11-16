/*
 * (c) Copyright 2026 Multiversio LLC. All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 */
package io.tileverse.geoserver.parquetry.migration;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.tileverse.geoserver.web.storage.migration.MigrationNote;
import io.tileverse.geoserver.web.storage.migration.MigrationPlan;

import io.tileverse.parquetry.geotools.parquet.GeoParquetDataStoreFactory;

/**
 * Translates the connection parameters of a community GeoParquet store into the parameters of the Parquetry store. The
 * community store reads its data through DuckDB. It takes its S3 settings from the query string of its URI and from its
 * own parameters; the Parquetry store takes them as {@code storage.s3.*} parameters. Target values are text, as
 * GeoServer persists them.
 */
final class GeoParquetTranslation {

    private static final String TARGET_TYPE = new GeoParquetDataStoreFactory().getDisplayName();

    private static final String URI = "uri";
    private static final String AWS_REGION = "aws_region";
    private static final String AWS_PROFILE = "aws_profile";
    private static final String ENDPOINT = "endpoint";
    private static final String URL_STYLE = "url_style";
    private static final String CREDENTIAL_CHAIN = "use_aws_credential_chain";
    private static final String NAMESPACE = "namespace";
    private static final String PRIMARY_KEY = "primary_key_id";
    private static final String MAX_HIVE_DEPTH = "max_hive_depth";
    private static final String DBTYPE = "dbtype";

    /** The community parameters with a meaning in the translation; the others are noted as dropped. */
    private static final Set<String> TRANSLATED_PARAMETERS = Set.of(
            URI,
            AWS_REGION,
            AWS_PROFILE,
            ENDPOINT,
            URL_STYLE,
            CREDENTIAL_CHAIN,
            NAMESPACE,
            PRIMARY_KEY,
            MAX_HIVE_DEPTH,
            DBTYPE);

    private static final List<String> REGION_QUERY_KEYS = List.of("region", "s3_region");
    private static final List<String> ACCESS_KEY_QUERY_KEYS = List.of("access_key", "s3_access_key_id");
    private static final List<String> SECRET_QUERY_KEYS = List.of("secret_key", "s3_secret_access_key");
    private static final List<String> ENDPOINT_QUERY_KEYS = List.of(ENDPOINT, "s3_endpoint");
    private static final List<String> URL_STYLE_QUERY_KEYS = List.of(URL_STYLE, "s3_url_style");
    private static final List<String> USE_SSL_QUERY_KEYS = List.of("s3_use_ssl");

    /** DuckDB reads a temporary AWS session token from these keys. The tool does not translate them. */
    private static final List<String> SESSION_TOKEN_QUERY_KEYS = List.of("session_token", "s3_session_token");

    private static final List<List<String>> KNOWN_QUERY_KEYS = List.of(
            REGION_QUERY_KEYS,
            ACCESS_KEY_QUERY_KEYS,
            SECRET_QUERY_KEYS,
            ENDPOINT_QUERY_KEYS,
            URL_STYLE_QUERY_KEYS,
            USE_SSL_QUERY_KEYS);

    private static final String S3_REGION = "storage.s3.region";
    private static final String S3_ACCESS_KEY = "storage.s3.aws-access-key-id";
    private static final String S3_SECRET_KEY = "storage.s3.aws-secret-access-key";
    private static final String S3_ENDPOINT = "storage.s3.endpoint";
    private static final String S3_PATH_STYLE = "storage.s3.force-path-style";
    private static final String S3_PROFILE = "storage.s3.default-credentials-profile";
    private static final String S3_ANONYMOUS = "storage.s3.anonymous";

    private static final String PATH_STYLE = "path";
    private static final String TRUE = "true";
    private static final String FALSE = "false";
    private static final String HTTPS = "https://";
    private static final String HTTP = "http://";

    /** The parameters of the community store, in their stored order. */
    private final Map<String, Serializable> community;

    /** The plan under construction. */
    private final MigrationPlan.Builder plan = MigrationPlan.to(TARGET_TYPE);

    private GeoParquetTranslation(Map<String, Serializable> community) {
        this.community = community;
    }

    static MigrationPlan translate(Map<String, Serializable> communityParameters) {
        return new GeoParquetTranslation(communityParameters).translate();
    }

    /**
     * The values of the secret keys in the query of a community uri, as written in it. The community store puts them in
     * its layer names in this form, mangled by its naming rule.
     */
    static List<String> writtenSecrets(String uriText) {
        Map<String, String> writtenQuery = CommunityUri.rawQuery(uriText);
        List<String> secrets = new ArrayList<>();
        for (String key : secretQueryKeys()) {
            String writtenValue = writtenQuery.get(key);
            if (hasText(writtenValue)) {
                secrets.add(writtenValue);
            }
        }
        return secrets;
    }

    private MigrationPlan translate() {
        addSourceDisplay();
        Optional<String> uriText = parameter(URI);
        if (uriText.isEmpty()) {
            plan.note(MigrationNote.blocker("GeoParquetMigration.noUri"));
            return plan.build();
        }
        CommunityUri uri = CommunityUri.parse(uriText.get());
        registerQuerySecrets(uriText.get(), uri);
        translateStoreParameters(uri);
        translateS3Settings(uri);
        noteDroppedSettings();
        saveHiveDepth();
        return plan.build();
    }

    private void addSourceDisplay() {
        community.forEach((key, value) -> plan.source(key, sourceValue(key, value)));
    }

    /**
     * Registers the secret query values as secrets of the plan, to scrub them from messages. A value is registered as
     * decoded and as written in the URI, because a message may quote either form. The values of unknown keys are not
     * registered: the Parquetry store never receives them, and scrubbing a short value would garble the displays.
     */
    private void registerQuerySecrets(String uriText, CommunityUri uri) {
        Map<String, String> writtenQuery = CommunityUri.rawQuery(uriText);
        for (String key : secretQueryKeys()) {
            String decodedValue = uri.query().get(key);
            if (hasText(decodedValue)) {
                plan.secretValue(decodedValue);
                plan.secretValue(writtenQuery.get(key));
            }
        }
    }

    private void translateStoreParameters(CommunityUri uri) {
        plan.parameter(GeoParquetDataStoreFactory.GEOPARQUET_URI.key, uri.location());
        parameter(NAMESPACE)
                .ifPresent(namespace -> plan.parameter(GeoParquetDataStoreFactory.NAMESPACE.key, namespace));
        parameter(PRIMARY_KEY).ifPresent(column -> plan.parameter(GeoParquetDataStoreFactory.FID.key, column));
    }

    private void translateS3Settings(CommunityUri uri) {
        blockUnknownQueryKeys(uri);
        translateRegion(uri);
        translateCredentials(uri);
        translateEndpoint(uri);
        translateUrlStyle(uri);
    }

    /**
     * Notes the settings left out in one note. GeoServer saves the defaults of the community factory with each store,
     * and one note per setting would flood the details of the store.
     */
    private void noteDroppedSettings() {
        List<String> droppedKeys = new ArrayList<>();
        for (String key : community.keySet()) {
            if (isDropped(key)) {
                droppedKeys.add(key);
            }
        }
        if (!droppedKeys.isEmpty()) {
            String keyList = String.join(", ", droppedKeys);
            plan.note(MigrationNote.info("GeoParquetMigration.dropped", keyList));
        }
    }

    private void saveHiveDepth() {
        parameter(MAX_HIVE_DEPTH).ifPresent(depth -> plan.saveSetting(MAX_HIVE_DEPTH, depth));
    }

    private static String sourceValue(String key, Serializable value) {
        String text = value == null ? "" : String.valueOf(value);
        if (URI.equals(key) && hasText(text)) {
            CommunityUri uri = CommunityUri.parse(text);
            return uri.masked(maskedQueryKeys(uri));
        }
        return text;
    }

    /**
     * The query keys with a value hidden from the administrator: the secrets, and the keys unknown to the tool. The
     * tool cannot tell whether the value of an unknown key is a secret.
     */
    private static Set<String> maskedQueryKeys(CommunityUri uri) {
        Set<String> maskedKeys = secretQueryKeys();
        maskedKeys.addAll(unknownQueryKeys(uri));
        return maskedKeys;
    }

    private static Set<String> secretQueryKeys() {
        Set<String> secretKeys = new LinkedHashSet<>(SECRET_QUERY_KEYS);
        secretKeys.addAll(SESSION_TOKEN_QUERY_KEYS);
        return secretKeys;
    }

    private void blockUnknownQueryKeys(CommunityUri uri) {
        for (String key : unknownQueryKeys(uri)) {
            plan.note(MigrationNote.blocker("GeoParquetMigration.unknownQueryKey", key));
        }
    }

    private void translateRegion(CommunityUri uri) {
        Optional<String> region = queryValue(uri, REGION_QUERY_KEYS).or(() -> parameter(AWS_REGION));
        region.ifPresent(value -> plan.parameter(S3_REGION, value));
    }

    private void translateCredentials(CommunityUri uri) {
        Optional<String> accessKey = queryValue(uri, ACCESS_KEY_QUERY_KEYS);
        Optional<String> secretKey = queryValue(uri, SECRET_QUERY_KEYS);
        accessKey.ifPresent(value -> plan.parameter(S3_ACCESS_KEY, value));
        secretKey.ifPresent(value -> plan.secret(S3_SECRET_KEY, value));
        parameter(AWS_PROFILE).ifPresent(profile -> plan.parameter(S3_PROFILE, profile));
        if (readsAnonymously(uri, accessKey.isPresent())) {
            plan.parameter(S3_ANONYMOUS, TRUE);
            plan.note(MigrationNote.info("GeoParquetMigration.anonymous"));
        }
    }

    /**
     * S3-compatible services are often configured as a bare host and port; the Parquetry store needs a scheme. DuckDB
     * puts the scheme in front of such an endpoint according to {@code s3_use_ssl}.
     */
    private void translateEndpoint(CommunityUri uri) {
        Optional<String> endpoint = queryValue(uri, ENDPOINT_QUERY_KEYS).or(() -> parameter(ENDPOINT));
        if (endpoint.isEmpty()) {
            return;
        }
        String value = endpoint.get();
        if (value.contains("://")) {
            plan.parameter(S3_ENDPOINT, value);
            return;
        }
        String scheme = endpointScheme(uri);
        plan.parameter(S3_ENDPOINT, scheme + value);
        plan.note(MigrationNote.info("GeoParquetMigration.endpointScheme", scheme, value));
    }

    private void translateUrlStyle(CommunityUri uri) {
        Optional<String> urlStyle = queryValue(uri, URL_STYLE_QUERY_KEYS).or(() -> parameter(URL_STYLE));
        boolean pathStyle = urlStyle.filter(PATH_STYLE::equalsIgnoreCase).isPresent();
        if (pathStyle) {
            plan.parameter(S3_PATH_STYLE, TRUE);
        }
    }

    /** DuckDB reads over plain HTTP when {@code s3_use_ssl} is false, and over HTTPS otherwise. */
    private static String endpointScheme(CommunityUri uri) {
        Optional<String> useSsl = queryValue(uri, USE_SSL_QUERY_KEYS);
        boolean sslOff = useSsl.filter(FALSE::equalsIgnoreCase).isPresent();
        return sslOff ? HTTP : HTTPS;
    }

    private boolean isDropped(String key) {
        return !TRANSLATED_PARAMETERS.contains(key) && parameter(key).isPresent();
    }

    private static List<String> unknownQueryKeys(CommunityUri uri) {
        Set<String> queryKeys = uri.query().keySet();
        return queryKeys.stream().filter(key -> !isKnownQueryKey(key)).toList();
    }

    private static boolean isKnownQueryKey(String key) {
        return KNOWN_QUERY_KEYS.stream().anyMatch(aliases -> aliases.contains(key));
    }

    /**
     * Without credentials in the URI or from the AWS credential chain, DuckDB sends unsigned requests. The Parquetry
     * store would look up the default AWS credentials instead.
     */
    private boolean readsAnonymously(CommunityUri uri, boolean hasAccessKey) {
        boolean usesCredentialChain =
                parameter(CREDENTIAL_CHAIN).filter(TRUE::equalsIgnoreCase).isPresent();
        return uri.isS3() && !hasAccessKey && !usesCredentialChain;
    }

    /** The first non-blank query value among the aliases of a setting. */
    private static Optional<String> queryValue(CommunityUri uri, List<String> aliases) {
        return aliases.stream()
                .map(alias -> uri.query().get(alias))
                .filter(GeoParquetTranslation::hasText)
                .findFirst();
    }

    /** The text of a community parameter; a blank value counts as absent. */
    private Optional<String> parameter(String key) {
        Serializable value = community.get(key);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(String.valueOf(value)).filter(GeoParquetTranslation::hasText);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
