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
package io.tileverse.geoserver.web.storage;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.EnumChoiceRenderer;
import org.apache.wicket.markup.html.form.FormComponentPanel;
import org.apache.wicket.markup.html.form.NumberTextField;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.PropertyModel;

/**
 * An integer amount and a time unit composing one strict ISO-8601 duration string, the format parsed by
 * tileverse-storage.
 */
@SuppressWarnings("serial")
class DurationField extends FormComponentPanel<String> {

    /** The offered time units, in dropdown order. */
    static final List<ChronoUnit> UNITS =
            List.of(ChronoUnit.MILLIS, ChronoUnit.SECONDS, ChronoUnit.MINUTES, ChronoUnit.HOURS);

    private static final int NANOS_PER_MILLI = 1_000_000;
    private static final long MILLIS_PER_SECOND = 1_000;
    private static final long MILLIS_PER_MINUTE = 60 * MILLIS_PER_SECOND;
    private static final long MILLIS_PER_HOUR = 60 * MILLIS_PER_MINUTE;

    /** Backs the amount field; re-seeded with {@code unit} from the model before every render. */
    private Long amount;

    private ChronoUnit unit = ChronoUnit.SECONDS;

    private final NumberTextField<Long> amountField;
    private final DropDownChoice<ChronoUnit> unitChoice;

    DurationField(String id, IModel<String> model) {
        super(id, model);
        amountField = new NumberTextField<>("amount", new PropertyModel<>(this, "amount"), Long.class);
        amountField.setMinimum(0L);
        unitChoice =
                new DropDownChoice<>("unit", new PropertyModel<>(this, "unit"), UNITS, new EnumChoiceRenderer<>(this));
        add(amountField, unitChoice);
    }

    @Override
    protected void onBeforeRender() {
        Decomposed decomposed = decompose(getModelObject());
        amount = decomposed.amount();
        unit = decomposed.unit();
        super.onBeforeRender();
    }

    /** A blank amount converts to {@code null}, unsetting the parameter. */
    @Override
    public void convertInput() {
        Long amountInput = amountField.getConvertedInput();
        if (amountInput == null) {
            setConvertedInput(null);
            return;
        }
        ChronoUnit unitInput = unitChoice.getConvertedInput();
        setConvertedInput(compose(amountInput, unitInput != null ? unitInput : unit));
    }

    /** A duration as shown by the widget. */
    record Decomposed(Long amount, ChronoUnit unit) {}

    /** Uses the largest unit holding the duration as a whole number; a sub-millisecond remainder truncates. */
    static Decomposed decompose(String iso) {
        Duration duration = parseOrNull(iso);
        if (duration == null) {
            return new Decomposed(null, ChronoUnit.SECONDS);
        }
        ChronoUnit unit = largestExactUnit(duration);
        return new Decomposed(amountIn(duration, unit), unit);
    }

    static String compose(long amount, ChronoUnit unit) {
        return Duration.of(amount, unit).toString();
    }

    private static Duration parseOrNull(String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        try {
            return Duration.parse(iso);
        } catch (DateTimeParseException invalid) {
            return null;
        }
    }

    private static ChronoUnit largestExactUnit(Duration duration) {
        if (duration.isZero()) {
            return ChronoUnit.SECONDS;
        }
        if (duration.getNano() % NANOS_PER_MILLI != 0) {
            return ChronoUnit.MILLIS;
        }
        long millis = duration.toMillis();
        if (millis % MILLIS_PER_HOUR == 0) {
            return ChronoUnit.HOURS;
        }
        if (millis % MILLIS_PER_MINUTE == 0) {
            return ChronoUnit.MINUTES;
        }
        if (millis % MILLIS_PER_SECOND == 0) {
            return ChronoUnit.SECONDS;
        }
        return ChronoUnit.MILLIS;
    }

    private static Long amountIn(Duration duration, ChronoUnit unit) {
        return switch (unit) {
            case HOURS -> duration.toHours();
            case MINUTES -> duration.toMinutes();
            case SECONDS -> duration.getSeconds();
            default -> duration.toMillis();
        };
    }
}
