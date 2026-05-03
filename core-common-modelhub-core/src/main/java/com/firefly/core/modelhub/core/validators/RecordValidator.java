/*
 * Copyright 2025 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.firefly.core.modelhub.core.validators;

import com.firefly.core.modelhub.interfaces.dtos.VirtualEntityFieldDto;
import com.firefly.core.modelhub.interfaces.enums.FieldType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Validator for record payloads against entity field definitions.
 */
@Component
@Slf4j
public class RecordValidator {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");
    private static final Pattern URL_PATTERN = Pattern.compile("^(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9\\-\\s]+$");

    /**
     * Validates a record payload against a list of field definitions.
     *
     * @param payload The record payload to validate
     * @param fields The field definitions to validate against
     * @return A Mono that completes successfully if validation passes, or errors with ValidationException if validation fails
     */
    public Mono<Void> validateRecord(Map<String, Object> payload, Flux<VirtualEntityFieldDto> fields) {
        return fields.collectList()
                .flatMap(fieldList -> {
                    List<String> validationErrors = new ArrayList<>();

                    // Check for required fields
                    for (VirtualEntityFieldDto field : fieldList) {
                        if (Boolean.TRUE.equals(field.getRequired()) && !payload.containsKey(field.getFieldKey())) {
                            validationErrors.add("Required field '" + field.getFieldKey() + "' is missing");
                            continue;
                        }

                        if (payload.containsKey(field.getFieldKey())) {
                            Object value = payload.get(field.getFieldKey());

                            // Skip validation for null values in non-required fields
                            if (value == null) {
                                if (Boolean.TRUE.equals(field.getRequired())) {
                                    validationErrors.add("Required field '" + field.getFieldKey() + "' cannot be null");
                                }
                                continue;
                            }

                            // Validate field value based on field type
                            String error = validateFieldValue(field, value);
                            if (error != null) {
                                validationErrors.add(error);
                            }
                        }
                    }

                    if (!validationErrors.isEmpty()) {
                        return Mono.error(new ValidationException("Record validation failed", validationErrors));
                    }

                    return Mono.empty();
                });
    }

    /**
     * Validates a field value against its field definition.
     *
     * @param field The field definition
     * @param value The value to validate
     * @return An error message if validation fails, or null if validation passes
     */
    private String validateFieldValue(VirtualEntityFieldDto field, Object value) {
        String fieldKey = field.getFieldKey();
        FieldType fieldType = FieldType.fromValue(field.getFieldType());

        if (fieldType == null) {
            return "Unknown field type for field '" + fieldKey + "'";
        }

        switch (fieldType) {
            case STRING:
                if (!(value instanceof String)) {
                    return "Field '" + fieldKey + "' must be a string";
                }
                break;

            case NUMBER:
                if (!(value instanceof Number) && !(value instanceof String && isNumeric((String) value))) {
                    return "Field '" + fieldKey + "' must be a number";
                }
                break;

            case INTEGER:
                if (value instanceof String) {
                    try {
                        Integer.parseInt((String) value);
                    } catch (NumberFormatException e) {
                        return "Field '" + fieldKey + "' must be an integer";
                    }
                } else if (!(value instanceof Integer) && !(value instanceof Long)) {
                    return "Field '" + fieldKey + "' must be an integer";
                }
                break;

            case BOOLEAN:
                if (!(value instanceof Boolean) && 
                    !(value instanceof String && ("true".equalsIgnoreCase((String) value) || "false".equalsIgnoreCase((String) value)))) {
                    return "Field '" + fieldKey + "' must be a boolean";
                }
                break;

            case DATE:
                if (value instanceof String) {
                    try {
                        LocalDate.parse((String) value);
                    } catch (DateTimeParseException e) {
                        return "Field '" + fieldKey + "' must be a valid date (YYYY-MM-DD)";
                    }
                } else if (!(value instanceof LocalDate)) {
                    return "Field '" + fieldKey + "' must be a valid date";
                }
                break;

            case DATETIME:
                if (value instanceof String) {
                    try {
                        LocalDateTime.parse((String) value, DateTimeFormatter.ISO_DATE_TIME);
                    } catch (DateTimeParseException e) {
                        return "Field '" + fieldKey + "' must be a valid datetime (ISO format)";
                    }
                } else if (!(value instanceof LocalDateTime)) {
                    return "Field '" + fieldKey + "' must be a valid datetime";
                }
                break;

            case EMAIL:
                if (!(value instanceof String) || !EMAIL_PATTERN.matcher((String) value).matches()) {
                    return "Field '" + fieldKey + "' must be a valid email address";
                }
                break;

            case URL:
                if (!(value instanceof String) || !URL_PATTERN.matcher((String) value).matches()) {
                    return "Field '" + fieldKey + "' must be a valid URL";
                }
                break;

            case PHONE:
                if (!(value instanceof String) || !PHONE_PATTERN.matcher((String) value).matches()) {
                    return "Field '" + fieldKey + "' must be a valid phone number";
                }
                break;

            case ENUM:
                if (!(value instanceof String)) {
                    return "Field '" + fieldKey + "' must be a string";
                }

                // Check if the value is in the allowed options
                if (field.getOptions() != null) {
                    try {
                        Map<String, Object> options = (Map<String, Object>) field.getOptions();
                        if (options.containsKey("values")) {
                            List<String> allowedValues = (List<String>) options.get("values");
                            if (!allowedValues.contains(value)) {
                                return "Field '" + fieldKey + "' must be one of: " + String.join(", ", allowedValues);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Failed to validate enum field '{}': {}", fieldKey, e.getMessage());
                    }
                }
                break;

            case OBJECT:
                if (!(value instanceof Map)) {
                    return "Field '" + fieldKey + "' must be an object";
                }
                break;

            case ARRAY:
                if (!(value instanceof List)) {
                    return "Field '" + fieldKey + "' must be an array";
                }

                // If options specify item type, validate each item
                if (field.getOptions() != null) {
                    try {
                        Map<String, Object> options = (Map<String, Object>) field.getOptions();
                        if (options.containsKey("itemType")) {
                            String itemType = (String) options.get("itemType");
                            FieldType itemFieldType = FieldType.fromValue(itemType);

                            if (itemFieldType != null) {
                                List<Object> items = (List<Object>) value;
                                for (int i = 0; i < items.size(); i++) {
                                    Object item = items.get(i);

                                    // Create a temporary field for validation
                                    VirtualEntityFieldDto tempField = VirtualEntityFieldDto.builder()
                                            .fieldKey(fieldKey + "[" + i + "]")
                                            .fieldType(itemType)
                                            .build();

                                    String itemError = validateFieldValue(tempField, item);
                                    if (itemError != null) {
                                        return itemError;
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Failed to validate array items for field '{}': {}", fieldKey, e.getMessage());
                    }
                }
                break;

            case REFERENCE:
                if (!(value instanceof String) && !(value instanceof UUID)) {
                    return "Field '" + fieldKey + "' must be a valid reference ID";
                }

                // Validate Long format if it's a string
                if (value instanceof String) {
                    try {
                        UUID.fromString((String) value);
                    } catch (IllegalArgumentException e) {
                        return "Field '" + fieldKey + "' must be a valid UUID";
                    }
                }
                break;
        }

        return null;
    }

    private boolean isNumeric(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Exception thrown when record validation fails.
     */
    public static class ValidationException extends RuntimeException {
        private final List<String> errors;

        public ValidationException(String message, List<String> errors) {
            super(message);
            this.errors = errors;
        }

        public List<String> getErrors() {
            return errors;
        }
    }
}
