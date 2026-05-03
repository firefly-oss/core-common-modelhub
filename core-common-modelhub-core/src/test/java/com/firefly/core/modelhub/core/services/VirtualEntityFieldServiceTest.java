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


package com.firefly.core.modelhub.core.services;

import com.firefly.core.modelhub.core.cache.EntityDefinitionCache;
import com.firefly.core.modelhub.core.mappers.VirtualEntityFieldMapper;
import com.firefly.core.modelhub.interfaces.dtos.VirtualEntityFieldDto;
import com.firefly.core.modelhub.interfaces.enums.FieldType;
import com.firefly.core.modelhub.models.entities.VirtualEntity;
import com.firefly.core.modelhub.models.entities.VirtualEntityField;
import com.firefly.core.modelhub.models.repositories.VirtualEntityFieldRepository;
import com.firefly.core.modelhub.models.repositories.VirtualEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class VirtualEntityFieldServiceTest {

    @Mock
    private VirtualEntityFieldRepository fieldRepository;

    @Mock
    private VirtualEntityRepository entityRepository;

    @Mock
    private VirtualEntityFieldMapper fieldMapper;

    @Mock
    private EntityDefinitionCache entityCache;

    @InjectMocks
    private VirtualEntityFieldService fieldService;

    private UUID entityId;
    private UUID fieldId;
    private VirtualEntity entity;
    private VirtualEntityField field;
    private VirtualEntityFieldDto fieldDto;

    @BeforeEach
    void setUp() {
        entityId = UUID.randomUUID();
        fieldId = UUID.randomUUID();

        entity = VirtualEntity.builder()
                .id(entityId)
                .name("TestEntity")
                .description("Test Entity Description")
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        field = VirtualEntityField.builder()
                .id(fieldId)
                .entityId(entityId)
                .fieldKey("testField")
                .fieldLabel("Test Field")
                .fieldType("string")
                .required(true)
                .orderIndex(1)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        fieldDto = VirtualEntityFieldDto.builder()
                .id(fieldId)
                .entityId(entityId)
                .fieldKey("testField")
                .fieldLabel("Test Field")
                .fieldType("string")
                .required(true)
                .orderIndex(1)
                .build();
    }

    @Test
    void getFieldsByEntityId_ShouldReturnFields() {
        when(entityCache.getFieldsByEntityId(eq(entityId), any())).thenReturn(Flux.just(fieldDto));

        StepVerifier.create(fieldService.getFieldsByEntityId(entityId))
                .expectNext(fieldDto)
                .verifyComplete();

        verify(entityCache).getFieldsByEntityId(eq(entityId), any());
    }

    @Test
    void getFieldById_ShouldReturnField() {
        when(fieldRepository.findById(fieldId)).thenReturn(Mono.just(field));
        when(fieldMapper.toDto(field)).thenReturn(fieldDto);

        StepVerifier.create(fieldService.getFieldById(fieldId))
                .expectNext(fieldDto)
                .verifyComplete();

        verify(fieldRepository).findById(fieldId);
        verify(fieldMapper).toDto(field);
    }

    @Test
    void getFieldById_WhenNotFound_ShouldReturnEmpty() {
        when(fieldRepository.findById(fieldId)).thenReturn(Mono.empty());

        StepVerifier.create(fieldService.getFieldById(fieldId))
                .verifyComplete();

        verify(fieldRepository).findById(fieldId);
        verify(fieldMapper, never()).toDto(any());
    }

    @Test
    void createField_ShouldCreateAndReturnField() {
        when(entityRepository.findById(entityId)).thenReturn(Mono.just(entity));
        when(fieldRepository.findByEntityIdAndFieldKey(entityId, "testField"))
                .thenReturn(Mono.empty());
        when(fieldMapper.toEntity(fieldDto)).thenReturn(field);
        when(fieldRepository.save(any(VirtualEntityField.class))).thenReturn(Mono.just(field));
        when(fieldMapper.toDto(field)).thenReturn(fieldDto);
        doNothing().when(entityCache).invalidateFields(entityId);

        StepVerifier.create(fieldService.createField(fieldDto))
                .expectNext(fieldDto)
                .verifyComplete();

        verify(entityRepository).findById(entityId);
        verify(fieldRepository).findByEntityIdAndFieldKey(entityId, "testField");
        verify(fieldMapper).toEntity(fieldDto);
        verify(fieldRepository).save(any(VirtualEntityField.class));
        verify(fieldMapper).toDto(field);
        verify(entityCache).invalidateFields(entityId);
    }

    @Test
    void createField_WhenEntityNotFound_ShouldReturnError() {
        when(entityRepository.findById(entityId)).thenReturn(Mono.empty());

        StepVerifier.create(fieldService.createField(fieldDto))
                .expectErrorMatches(throwable -> 
                    throwable instanceof IllegalArgumentException && 
                    throwable.getMessage().contains("Entity with ID"))
                .verify();

        verify(entityRepository).findById(entityId);
        verify(fieldRepository, never()).findByEntityIdAndFieldKey(any(), any());
        verify(fieldMapper, never()).toEntity(any());
        verify(fieldRepository, never()).save(any());
    }

    @Test
    void createField_WhenFieldKeyExists_ShouldReturnError() {
        when(entityRepository.findById(entityId)).thenReturn(Mono.just(entity));
        when(fieldRepository.findByEntityIdAndFieldKey(entityId, "testField"))
                .thenReturn(Mono.just(field));

        StepVerifier.create(fieldService.createField(fieldDto))
                .expectErrorMatches(throwable -> 
                    throwable instanceof IllegalArgumentException && 
                    throwable.getMessage().contains("Field with key"))
                .verify();

        verify(entityRepository).findById(entityId);
        verify(fieldRepository).findByEntityIdAndFieldKey(entityId, "testField");
        verify(fieldMapper, never()).toEntity(any());
        verify(fieldRepository, never()).save(any());
    }

    @Test
    void updateField_ShouldUpdateAndReturnField() {
        when(fieldRepository.findById(fieldId)).thenReturn(Mono.just(field));
        when(fieldMapper.toEntity(fieldDto)).thenReturn(field);
        when(fieldRepository.save(any(VirtualEntityField.class))).thenReturn(Mono.just(field));
        when(fieldMapper.toDto(field)).thenReturn(fieldDto);
        doNothing().when(entityCache).invalidateFields(entityId);

        StepVerifier.create(fieldService.updateField(fieldId, fieldDto))
                .expectNext(fieldDto)
                .verifyComplete();

        verify(fieldRepository).findById(fieldId);
        verify(fieldMapper).toEntity(fieldDto);
        verify(fieldRepository).save(any(VirtualEntityField.class));
        verify(fieldMapper).toDto(field);
        verify(entityCache).invalidateFields(entityId);
    }

    @Test
    void updateField_WhenNotFound_ShouldReturnError() {
        when(fieldRepository.findById(fieldId)).thenReturn(Mono.empty());

        StepVerifier.create(fieldService.updateField(fieldId, fieldDto))
                .expectErrorMatches(throwable -> 
                    throwable instanceof IllegalArgumentException && 
                    throwable.getMessage().contains("Field with ID"))
                .verify();

        verify(fieldRepository).findById(fieldId);
        verify(fieldMapper, never()).toEntity(any());
        verify(fieldRepository, never()).save(any());
    }

    @Test
    void deleteField_ShouldDeleteField() {
        when(fieldRepository.findById(fieldId)).thenReturn(Mono.just(field));
        when(fieldRepository.deleteById(fieldId)).thenReturn(Mono.empty());
        doNothing().when(entityCache).invalidateFields(entityId);

        StepVerifier.create(fieldService.deleteField(fieldId))
                .verifyComplete();

        verify(fieldRepository).findById(fieldId);
        verify(fieldRepository).deleteById(fieldId);
        verify(entityCache).invalidateFields(entityId);
    }

    @Test
    void deleteField_WhenNotFound_ShouldReturnError() {
        when(fieldRepository.findById(fieldId)).thenReturn(Mono.empty());

        StepVerifier.create(fieldService.deleteField(fieldId))
                .expectErrorMatches(throwable -> 
                    throwable instanceof IllegalArgumentException && 
                    throwable.getMessage().contains("Field with ID"))
                .verify();

        verify(fieldRepository).findById(fieldId);
        verify(fieldRepository, never()).deleteById(any(UUID.class));
    }

    @Test
    void deleteFieldsByEntityId_ShouldDeleteFields() {
        when(fieldRepository.deleteByEntityId(entityId)).thenReturn(Mono.empty());
        doNothing().when(entityCache).invalidateFields(entityId);

        StepVerifier.create(fieldService.deleteFieldsByEntityId(entityId))
                .verifyComplete();

        verify(fieldRepository).deleteByEntityId(entityId);
        verify(entityCache).invalidateFields(entityId);
    }
}
