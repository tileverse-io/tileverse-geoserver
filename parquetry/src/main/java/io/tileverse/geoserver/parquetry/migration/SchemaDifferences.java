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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.geoserver.catalog.AttributeTypeInfo;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.feature.type.AttributeDescriptor;
import org.geotools.api.filter.expression.Expression;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.filter.FilterAttributeExtractor;
import org.geotools.filter.text.cql2.CQLException;
import org.geotools.filter.text.ecql.ECQL;
import org.geotools.referencing.CRS;

import io.tileverse.geoserver.web.storage.migration.MigrationNote;

/** The differences between the schema of a layer before the migration and the schema found by the Parquetry store. */
final class SchemaDifferences {

    /** Shown for a layer without CRS, such as a table without geometry. */
    private static final String NO_CRS = "none";

    private static final String ATTRIBUTE_MISSING = "GeoParquetMigration.attributeMissing";

    private static final String BOX_COLUMN_HIDDEN = "GeoParquetMigration.boxColumnHidden";

    private static final String SAVED_ATTRIBUTE_MISSING = "GeoParquetMigration.savedAttributeMissing";

    private static final String SAVED_BOX_COLUMN = "GeoParquetMigration.savedBoxColumn";

    private SchemaDifferences() {}

    /** A note when the found CRS differs from the saved one. A layer saved without CRS is not compared. */
    static List<MigrationNote> crs(CoordinateReferenceSystem saved, CoordinateReferenceSystem found) {
        if (saved == null || CRS.equalsIgnoreMetadata(saved, found)) {
            return List.of();
        }
        MigrationNote note =
                MigrationNote.difference("GeoParquetMigration.crsDiffers", identifierOf(saved), identifierOf(found));
        return List.of(note);
    }

    /**
     * The attributes missing or retyped in {@code after}, then the attributes new in {@code after}. An attribute read
     * from one of the {@code boxColumns} gets its own note: the Parquetry store hides such a column on purpose.
     */
    static List<MigrationNote> attributes(SimpleFeatureType before, SimpleFeatureType after, Set<String> boxColumns) {
        List<MigrationNote> notes = new ArrayList<>(changedAttributes(before, after, boxColumns));
        notes.addAll(addedAttributes(before, after));
        return notes;
    }

    /**
     * A blocker for each saved attribute reading a property missing in {@code after}. A layer with saved attributes
     * reads them from its store by their source, and a read fails when the store lacks one of their properties. An
     * attribute reading box columns only gets its own message.
     */
    static List<MigrationNote> missingSources(
            List<AttributeTypeInfo> savedAttributes, SimpleFeatureType after, Set<String> boxColumns) {
        List<MigrationNote> notes = new ArrayList<>();
        for (AttributeTypeInfo attribute : savedAttributes) {
            Set<String> missing = missingProperties(attribute, after);
            if (!missing.isEmpty()) {
                notes.add(MigrationNote.blocker(savedMissingKey(missing, boxColumns), attribute.getName()));
            }
        }
        return notes;
    }

    /** The attributes of {@code before} with another type in {@code after}. */
    static List<MigrationNote> retypedAttributes(SimpleFeatureType before, SimpleFeatureType after) {
        List<MigrationNote> notes = new ArrayList<>();
        for (AttributeDescriptor attribute : before.getAttributeDescriptors()) {
            AttributeDescriptor counterpart = after.getDescriptor(attribute.getLocalName());
            if (counterpart != null) {
                notes.addAll(retyping(attribute, counterpart));
            }
        }
        return notes;
    }

    private static String identifierOf(CoordinateReferenceSystem crs) {
        String identifier = CRS.toSRS(crs);
        return identifier == null ? NO_CRS : identifier;
    }

    private static List<MigrationNote> changedAttributes(
            SimpleFeatureType before, SimpleFeatureType after, Set<String> boxColumns) {
        List<MigrationNote> notes = new ArrayList<>();
        for (AttributeDescriptor attribute : before.getAttributeDescriptors()) {
            AttributeDescriptor counterpart = after.getDescriptor(attribute.getLocalName());
            notes.addAll(changesOf(attribute, counterpart, boxColumns));
        }
        return notes;
    }

    /** The counterpart is null when {@code after} has no attribute of that name. */
    private static List<MigrationNote> changesOf(
            AttributeDescriptor attribute, AttributeDescriptor counterpart, Set<String> boxColumns) {
        if (counterpart == null) {
            String name = attribute.getLocalName();
            String key = boxColumns.contains(name) ? BOX_COLUMN_HIDDEN : ATTRIBUTE_MISSING;
            return List.of(MigrationNote.difference(key, name));
        }
        return retyping(attribute, counterpart);
    }

    /**
     * An attribute of type {@link Object} has an unknown type, such as an attribute saved in the catalog without
     * binding: only its name is compared.
     */
    private static List<MigrationNote> retyping(AttributeDescriptor attribute, AttributeDescriptor counterpart) {
        String name = attribute.getLocalName();
        Class<?> binding = attribute.getType().getBinding();
        Class<?> foundBinding = counterpart.getType().getBinding();
        if (binding.equals(Object.class) || binding.equals(foundBinding)) {
            return List.of();
        }
        MigrationNote retyped = MigrationNote.difference(
                "GeoParquetMigration.attributeRetyped", name, binding.getSimpleName(), foundBinding.getSimpleName());
        return List.of(retyped);
    }

    private static List<MigrationNote> addedAttributes(SimpleFeatureType before, SimpleFeatureType after) {
        List<MigrationNote> notes = new ArrayList<>();
        for (AttributeDescriptor attribute : after.getAttributeDescriptors()) {
            String name = attribute.getLocalName();
            if (before.getDescriptor(name) == null) {
                notes.add(MigrationNote.info("GeoParquetMigration.attributeAdded", name));
            }
        }
        return notes;
    }

    private static Set<String> missingProperties(AttributeTypeInfo attribute, SimpleFeatureType after) {
        Set<String> properties = sourceProperties(attribute);
        return properties.stream()
                .filter(property -> after.getDescriptor(property) == null)
                .collect(Collectors.toSet());
    }

    private static String savedMissingKey(Set<String> missing, Set<String> boxColumns) {
        return boxColumns.containsAll(missing) ? SAVED_BOX_COLUMN : SAVED_ATTRIBUTE_MISSING;
    }

    /** The properties read by a saved attribute: those of its source expression when it has one, else its name. */
    private static Set<String> sourceProperties(AttributeTypeInfo attribute) {
        String source = attribute.getRawSource();
        if (source == null || source.isBlank()) {
            return Set.of(attribute.getName());
        }
        return propertiesOf(source);
    }

    /**
     * The properties read by a CQL expression. GeoServer refuses to serve a layer with an unparsable source, before and
     * after the migration: such a source reads no property here.
     */
    private static Set<String> propertiesOf(String source) {
        try {
            Expression expression = ECQL.toExpression(source);
            FilterAttributeExtractor extractor = new FilterAttributeExtractor();
            expression.accept(extractor, null);
            return extractor.getAttributeNameSet();
        } catch (CQLException unparsable) {
            return Set.of();
        }
    }
}
