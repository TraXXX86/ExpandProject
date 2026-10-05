package fr.expand.project.importdata.validation;

import fr.expand.project.importdata.model.generated.ATTRIBUTETYPE;
import fr.expand.project.importdata.util.CypherUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** Value rules shared by model defaults and submitted object/link attributes. */
public final class AttributeValues {
    private AttributeValues() {}

    public static boolean isReservedKey(String key) {
        return CypherUtils.isReservedProperty(key);
    }

    public static boolean isValid(String value, ATTRIBUTETYPE type) {
        if (value == null || value.isBlank()) {
            return type == null || type == ATTRIBUTETYPE.STRING;
        }
        try {
            switch (type == null ? ATTRIBUTETYPE.STRING : type) {
                case INTEGER:
                    Integer.parseInt(value);
                    return true;
                case DOUBLE:
                    return Double.isFinite(Double.parseDouble(value));
                case BOOLEAN:
                    return value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false");
                case DATE:
                    return isIsoDate(value);
                case STRING:
                default:
                    return true;
            }
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // DATE accepts a calendar date, local date-time, or date-time with an offset.
    // ISO formatters use strict calendar resolution (e.g. February 30 is rejected).
    private static boolean isIsoDate(String value) {
        try {
            LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
            return true;
        } catch (DateTimeParseException ignored) {
        }
        try {
            LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            return true;
        } catch (DateTimeParseException ignored) {
        }
        try {
            OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            return true;
        } catch (DateTimeParseException ignored) {
            return false;
        }
    }
}
