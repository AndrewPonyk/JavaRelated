package com.loanorigination.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Global JPA converter that maps Java {@link Boolean} to {@code 'Y'} and
 * {@code 'N'} string values.
 * This ensures consistency across the application when dealing with legacy
 * schema conventions.
 * 
 * NOTE: With autoApply = true, all boolean fields in entities will use this
 * conversion unless
 * overridden with an explicit @Convert annotation.
 */
@Converter(autoApply = true)
public class BooleanToStringConverter implements AttributeConverter<Boolean, String> {

    @Override
    public String convertToDatabaseColumn(Boolean attribute) {
        return (attribute != null && attribute) ? "Y" : "N";
    }

    @Override
    public Boolean convertToEntityAttribute(String dbData) {
        return "Y".equalsIgnoreCase(dbData);
    }
}
