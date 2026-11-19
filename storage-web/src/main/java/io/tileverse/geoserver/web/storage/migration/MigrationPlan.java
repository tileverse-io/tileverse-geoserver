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
package io.tileverse.geoserver.web.storage.migration;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The translation of a store to its tileverse type: the target type, url and parameters, the renamed native names, the
 * metadata to drop, and the notes for the administrator. A plan also knows the secrets of the store, to mask them in
 * displays and messages.
 */
public final class MigrationPlan {

    public static final String MASK = "********";

    /** The store type written to the migrated store. */
    private final String targetType;

    /** Null when the target store has no url. */
    private final String targetUrl;

    /** The connection parameters of the target store, in insertion order. */
    private final Map<String, Serializable> targetParameters;

    /** The keys of the target parameters to mask in displays. */
    private final Set<String> secretKeys;

    /** The original parameters as shown to the administrator, already masked. */
    private final Map<String, String> sourceDisplay;

    /** Resource id to native name after the migration; an empty name blanks the native name. */
    private final Map<String, Optional<String>> nativeNames;

    /** The metadata keys removed from the store by the migration. */
    private final Set<String> removedMetadataKeys;

    /** Extra facts kept in the backup. */
    private final Map<String, String> savedSettings;

    /** The texts to mask in any message. */
    private final Set<String> secretValues;

    /** The notes for the administrator, in insertion order. */
    private final List<MigrationNote> notes;

    private MigrationPlan(Builder builder) {
        this.targetType = builder.targetType;
        this.targetUrl = builder.targetUrl;
        this.targetParameters = Collections.unmodifiableMap(new LinkedHashMap<>(builder.targetParameters));
        this.secretKeys = Collections.unmodifiableSet(new LinkedHashSet<>(builder.secretKeys));
        this.sourceDisplay = Collections.unmodifiableMap(new LinkedHashMap<>(builder.sourceDisplay));
        this.nativeNames = Collections.unmodifiableMap(new LinkedHashMap<>(builder.nativeNames));
        this.removedMetadataKeys = Collections.unmodifiableSet(new LinkedHashSet<>(builder.removedMetadataKeys));
        this.savedSettings = Collections.unmodifiableMap(new LinkedHashMap<>(builder.savedSettings));
        this.secretValues = Collections.unmodifiableSet(new LinkedHashSet<>(builder.secretValues));
        this.notes = List.copyOf(builder.notes);
    }

    public static Builder to(String targetType) {
        return new Builder(targetType);
    }

    /** A builder holding a copy of this plan, to refine it without changing this plan. */
    public Builder toBuilder() {
        return new Builder(this);
    }

    public String targetType() {
        return targetType;
    }

    public Optional<String> targetUrl() {
        return Optional.ofNullable(targetUrl);
    }

    /** The connection parameters of the target store, in insertion order. */
    public Map<String, Serializable> targetParameters() {
        return targetParameters;
    }

    /** The original parameters as shown to the administrator, already masked. */
    public Map<String, String> sourceDisplay() {
        return sourceDisplay;
    }

    /** The target parameters as text, in insertion order, with the secret values masked and scrubbed. */
    public Map<String, String> targetDisplay() {
        Map<String, String> display = new LinkedHashMap<>();
        targetParameters.forEach((key, value) -> display.put(key, displayValue(key, value)));
        return Collections.unmodifiableMap(display);
    }

    /** Resource id to native name after the migration; an empty name blanks the native name. */
    public Map<String, Optional<String>> nativeNames() {
        return nativeNames;
    }

    public Set<String> removedMetadataKeys() {
        return removedMetadataKeys;
    }

    /** Extra facts kept in the backup. */
    public Map<String, String> savedSettings() {
        return savedSettings;
    }

    /** The texts to mask in any message. */
    public Set<String> secretValues() {
        return secretValues;
    }

    public List<MigrationNote> notes() {
        return notes;
    }

    /** Whether a BLOCKER note is present. */
    public boolean isBlocked() {
        return notes.stream().anyMatch(note -> note.severity() == MigrationNote.Severity.BLOCKER);
    }

    /**
     * Replaces each secret value in the message with {@link #MASK}. The longer secrets go first: a secret contained in
     * a longer one would otherwise leave the rest of the longer one in clear text.
     */
    public String scrub(String message) {
        if (message == null) {
            return null;
        }
        String scrubbed = message;
        for (String secret : secretsLongestFirst()) {
            scrubbed = scrubbed.replace(secret, MASK);
        }
        return scrubbed;
    }

    private String displayValue(String key, Serializable value) {
        if (secretKeys.contains(key)) {
            return MASK;
        }
        return scrub(String.valueOf(value));
    }

    private List<String> secretsLongestFirst() {
        Comparator<String> longestFirst =
                Comparator.comparingInt(String::length).reversed();
        return secretValues.stream()
                .filter(secret -> !secret.isBlank())
                .sorted(longestFirst)
                .toList();
    }

    /** Collects the parts of a plan. Maps and sets keep insertion order. */
    public static final class Builder {

        /** The store type written to the migrated store. */
        private final String targetType;

        /** Null until {@link #url(String)} is called. */
        private String targetUrl;

        /** The connection parameters of the target store, in insertion order. */
        private final Map<String, Serializable> targetParameters = new LinkedHashMap<>();

        /** The keys of the target parameters to mask in displays. */
        private final Set<String> secretKeys = new LinkedHashSet<>();

        /** The original parameters as shown to the administrator. */
        private final Map<String, String> sourceDisplay = new LinkedHashMap<>();

        /** Resource id to native name after the migration. */
        private final Map<String, Optional<String>> nativeNames = new LinkedHashMap<>();

        /** The metadata keys removed from the store. */
        private final Set<String> removedMetadataKeys = new LinkedHashSet<>();

        /** Extra facts kept in the backup. */
        private final Map<String, String> savedSettings = new LinkedHashMap<>();

        /** The texts to mask in any message. */
        private final Set<String> secretValues = new LinkedHashSet<>();

        /** The notes for the administrator. */
        private final List<MigrationNote> notes = new ArrayList<>();

        private Builder(String targetType) {
            this.targetType = Objects.requireNonNull(targetType, "targetType");
        }

        private Builder(MigrationPlan plan) {
            this(plan.targetType);
            this.targetUrl = plan.targetUrl;
            targetParameters.putAll(plan.targetParameters);
            secretKeys.addAll(plan.secretKeys);
            sourceDisplay.putAll(plan.sourceDisplay);
            nativeNames.putAll(plan.nativeNames);
            removedMetadataKeys.addAll(plan.removedMetadataKeys);
            savedSettings.putAll(plan.savedSettings);
            secretValues.addAll(plan.secretValues);
            notes.addAll(plan.notes);
        }

        public Builder url(String url) {
            this.targetUrl = url;
            return this;
        }

        public Builder parameter(String key, Serializable value) {
            targetParameters.put(key, value);
            return this;
        }

        /** Adds a parameter. Its value is masked in displays and scrubbed from messages. */
        public Builder secret(String key, Serializable value) {
            parameter(key, value);
            secretKeys.add(key);
            if (value != null) {
                secretValue(String.valueOf(value));
            }
            return this;
        }

        /** Adds an original parameter as shown to the administrator; the caller masks secrets. */
        public Builder source(String key, String shownValue) {
            sourceDisplay.put(key, shownValue);
            return this;
        }

        /** Adds a text to scrub from messages, such as a token embedded in a url. */
        public Builder secretValue(String value) {
            secretValues.add(value);
            return this;
        }

        public Builder nativeName(String resourceId, String name) {
            nativeNames.put(resourceId, Optional.of(name));
            return this;
        }

        public Builder blankNativeName(String resourceId) {
            nativeNames.put(resourceId, Optional.empty());
            return this;
        }

        public Builder removeMetadata(String key) {
            removedMetadataKeys.add(key);
            return this;
        }

        public Builder saveSetting(String name, String value) {
            savedSettings.put(name, value);
            return this;
        }

        public Builder note(MigrationNote note) {
            notes.add(Objects.requireNonNull(note, "note"));
            return this;
        }

        public MigrationPlan build() {
            return new MigrationPlan(this);
        }
    }
}
