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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
public class RecordValidatorTest {

    @InjectMocks
    private RecordValidator recordValidator;

    private VirtualEntityFieldDto stringField;
    private VirtualEntityFieldDto numberField;
    private VirtualEntityFieldDto booleanField;
    private VirtualEntityFieldDto emailField;
    private VirtualEntityFieldDto dateField;
    private VirtualEntityFieldDto requiredField;
    private VirtualEntityFieldDto enumField;
    private VirtualEntityFieldDto arrayField;
    private VirtualEntityFieldDto referenceField;

    @BeforeEach
    void setUp() {
        stringField = VirtualEntityFieldDto.builder()
                .id(UUID.randomUUID())
                .fieldKey("name")
                .fieldLabel("Name")
                .fieldType("string")
                .required(false)
                .orderIndex(1)
                .build();

        numberField = VirtualEntityFieldDto.builder()
                .id(UUID.randomUUID())
                .fieldKey("age")
                .fieldLabel("Age")
                .fieldType("number")
                .required(false)
                .orderIndex(2)
                .build();

        booleanField = VirtualEntityFieldDto.builder()
                .id(UUID.randomUUID())
                .fieldKey("active")
                .fieldLabel("Active")
                .fieldType("boolean")
                .required(false)
                .orderIndex(3)
                .build();

        emailField = VirtualEntityFieldDto.builder()
                .id(UUID.randomUUID())
                .fieldKey("email")
                .fieldLabel("Email")
                .fieldType("email")
                .required(false)
                .orderIndex(4)
                .build();

        dateField = VirtualEntityFieldDto.builder()
                .id(UUID.randomUUID())
                .fieldKey("birthDate")
                .fieldLabel("Birth Date")
                .fieldType("date")
                .required(false)
                .orderIndex(5)
                .build();

        requiredField = VirtualEntityFieldDto.builder()
                .id(UUID.randomUUID())
                .fieldKey("requiredField")
                .fieldLabel("Required Field")
                .fieldType("string")
                .required(true)
                .orderIndex(6)
                .build();

        // Create a map for enum options
        Map<String, Object> enumOptions = new HashMap<>();
        List<String> allowedValues = new ArrayList<>();
        allowedValues.add("option1");
        allowedValues.add("option2");
        allowedValues.add("option3");
        enumOptions.put("values", allowedValues);

        enumField = VirtualEntityFieldDto.builder()
                .id(UUID.randomUUID())
                .fieldKey("status")
                .fieldLabel("Status")
                .fieldType("enum")
                .required(false)
                .options(enumOptions)
                .orderIndex(7)
                .build();

        // Create a map for array options
        Map<String, Object> arrayOptions = new HashMap<>();
        arrayOptions.put("itemType", "string");

        arrayField = VirtualEntityFieldDto.builder()
                .id(UUID.randomUUID())
                .fieldKey("tags")
                .fieldLabel("Tags")
                .fieldType("array")
                .required(false)
                .options(arrayOptions)
                .orderIndex(8)
                .build();

        referenceField = VirtualEntityFieldDto.builder()
                .id(UUID.randomUUID())
                .fieldKey("categoryId")
                .fieldLabel("Category ID")
                .fieldType("reference")
                .required(false)
                .orderIndex(9)
                .build();
    }

    @Test
    void validateRecord_WithValidData_ShouldComplete() {
        // Given
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "John Doe");
        payload.put("age", 30);
        payload.put("active", true);
        payload.put("email", "john.doe@example.com");
        payload.put("birthDate", "2000-01-01");
        payload.put("requiredField", "Value");

        // When & Then
        StepVerifier.create(recordValidator.validateRecord(payload, 
                Flux.just(stringField, numberField, booleanField, emailField, dateField, requiredField)))
                .verifyComplete();
    }

    @Test
    void validateRecord_WithMissingRequiredField_ShouldFail() {
        // Given
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "John Doe");
        payload.put("age", 30);

        // When & Then
        StepVerifier.create(recordValidator.validateRecord(payload, Flux.just(requiredField)))
                .expectErrorMatches(throwable -> 
                    throwable instanceof RecordValidator.ValidationException &&
                    ((RecordValidator.ValidationException) throwable).getErrors().contains("Required field 'requiredField' is missing"))
                .verify();
    }

    @Test
    void validateRecord_WithInvalidEmail_ShouldFail() {
        // Given
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", "invalid-email");
        payload.put("requiredField", "Value");

        // When & Then
        StepVerifier.create(recordValidator.validateRecord(payload, Flux.just(emailField, requiredField)))
                .expectErrorMatches(throwable -> 
                    throwable instanceof RecordValidator.ValidationException &&
                    ((RecordValidator.ValidationException) throwable).getErrors().contains("Field 'email' must be a valid email address"))
                .verify();
    }

    @Test
    void validateRecord_WithInvalidNumber_ShouldFail() {
        // Given
        Map<String, Object> payload = new HashMap<>();
        payload.put("age", "not-a-number");
        payload.put("requiredField", "Value");

        // When & Then
        StepVerifier.create(recordValidator.validateRecord(payload, Flux.just(numberField, requiredField)))
                .expectErrorMatches(throwable -> 
                    throwable instanceof RecordValidator.ValidationException &&
                    ((RecordValidator.ValidationException) throwable).getErrors().contains("Field 'age' must be a number"))
                .verify();
    }

    @Test
    void validateRecord_WithInvalidBoolean_ShouldFail() {
        // Given
        Map<String, Object> payload = new HashMap<>();
        payload.put("active", "not-a-boolean");
        payload.put("requiredField", "Value");

        // When & Then
        StepVerifier.create(recordValidator.validateRecord(payload, Flux.just(booleanField, requiredField)))
                .expectErrorMatches(throwable -> 
                    throwable instanceof RecordValidator.ValidationException &&
                    ((RecordValidator.ValidationException) throwable).getErrors().contains("Field 'active' must be a boolean"))
                .verify();
    }

    @Test
    void validateRecord_WithInvalidDate_ShouldFail() {
        // Given
        Map<String, Object> payload = new HashMap<>();
        payload.put("birthDate", "not-a-date");
        payload.put("requiredField", "Value");

        // When & Then
        StepVerifier.create(recordValidator.validateRecord(payload, Flux.just(dateField, requiredField)))
                .expectErrorMatches(throwable -> 
                    throwable instanceof RecordValidator.ValidationException &&
                    ((RecordValidator.ValidationException) throwable).getErrors().contains("Field 'birthDate' must be a valid date (YYYY-MM-DD)"))
                .verify();
    }

    @Test
    void validateRecord_WithNullValueForRequiredField_ShouldFail() {
        // Given
        Map<String, Object> payload = new HashMap<>();
        payload.put("requiredField", null);

        // When & Then
        StepVerifier.create(recordValidator.validateRecord(payload, Flux.just(requiredField)))
                .expectErrorMatches(throwable -> 
                    throwable instanceof RecordValidator.ValidationException &&
                    ((RecordValidator.ValidationException) throwable).getErrors().contains("Required field 'requiredField' cannot be null"))
                .verify();
    }

    @Test
    void validateRecord_WithValidEnum_ShouldComplete() {
        // Given
        Map<String, Object> payload = new HashMap<>();
        payload.put("status", "option1");
        payload.put("requiredField", "Value");

        // When & Then
        StepVerifier.create(recordValidator.validateRecord(payload, Flux.just(enumField, requiredField)))
                .verifyComplete();
    }

    @Test
    void validateRecord_WithInvalidEnum_ShouldFail() {
        // Given
        Map<String, Object> payload = new HashMap<>();
        payload.put("status", "invalidOption");
        payload.put("requiredField", "Value");

        // When & Then
        StepVerifier.create(recordValidator.validateRecord(payload, Flux.just(enumField, requiredField)))
                .expectErrorMatches(throwable -> 
                    throwable instanceof RecordValidator.ValidationException &&
                    ((RecordValidator.ValidationException) throwable).getErrors().contains("Field 'status' must be one of: option1, option2, option3"))
                .verify();
    }

    @Test
    void validateRecord_WithValidArray_ShouldComplete() {
        // Given
        Map<String, Object> payload = new HashMap<>();
        List<String> tags = new ArrayList<>();
        tags.add("tag1");
        tags.add("tag2");
        payload.put("tags", tags);
        payload.put("requiredField", "Value");

        // When & Then
        StepVerifier.create(recordValidator.validateRecord(payload, Flux.just(arrayField, requiredField)))
                .verifyComplete();
    }

    @Test
    void validateRecord_WithInvalidArrayItem_ShouldFail() {
        // Given
        Map<String, Object> payload = new HashMap<>();
        List<Object> tags = new ArrayList<>();
        tags.add("tag1");
        tags.add(123); // Not a string
        payload.put("tags", tags);
        payload.put("requiredField", "Value");

        // When & Then
        StepVerifier.create(recordValidator.validateRecord(payload, Flux.just(arrayField, requiredField)))
                .expectErrorMatches(throwable -> 
                    throwable instanceof RecordValidator.ValidationException &&
                    ((RecordValidator.ValidationException) throwable).getErrors().contains("Field 'tags[1]' must be a string"))
                .verify();
    }

    @Test
    void validateRecord_WithValidReference_ShouldComplete() {
        // Given
        Map<String, Object> payload = new HashMap<>();
        payload.put("categoryId", UUID.randomUUID().toString());
        payload.put("requiredField", "Value");

        // When & Then
        StepVerifier.create(recordValidator.validateRecord(payload, Flux.just(referenceField, requiredField)))
                .verifyComplete();
    }

    @Test
    void validateRecord_WithInvalidReference_ShouldFail() {
        // Given
        Map<String, Object> payload = new HashMap<>();
        payload.put("categoryId", "not-a-uuid");
        payload.put("requiredField", "Value");

        // When & Then
        StepVerifier.create(recordValidator.validateRecord(payload, Flux.just(referenceField, requiredField)))
                .expectErrorMatches(throwable -> 
                    throwable instanceof RecordValidator.ValidationException &&
                    ((RecordValidator.ValidationException) throwable).getErrors().contains("Field 'categoryId' must be a valid UUID"))
                .verify();
    }
}
