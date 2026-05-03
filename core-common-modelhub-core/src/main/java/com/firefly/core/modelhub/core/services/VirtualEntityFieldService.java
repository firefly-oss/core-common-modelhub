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
import com.firefly.core.modelhub.interfaces.dtos.VirtualEntityFieldDto;
import com.firefly.core.modelhub.core.mappers.VirtualEntityFieldMapper;
import com.firefly.core.modelhub.models.entities.VirtualEntityField;
import com.firefly.core.modelhub.models.repositories.VirtualEntityFieldRepository;
import com.firefly.core.modelhub.models.repositories.VirtualEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service for virtual entity field operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VirtualEntityFieldService {

    private final VirtualEntityFieldRepository fieldRepository;
    private final VirtualEntityRepository entityRepository;
    private final VirtualEntityFieldMapper fieldMapper;
    private final EntityDefinitionCache entityCache;

    /**
     * Get all fields for a virtual entity.
     *
     * @param entityId the ID of the entity
     * @return a Flux emitting the fields
     */
    public Flux<VirtualEntityFieldDto> getFieldsByEntityId(UUID entityId) {
        return entityCache.getFieldsByEntityId(entityId, id -> 
            fieldRepository.findByEntityIdOrderByOrderIndex(id)
                .map(fieldMapper::toDto)
        );
    }

    /**
     * Get a field by ID.
     *
     * @param id the ID of the field
     * @return a Mono emitting the field or empty if not found
     */
    public Mono<VirtualEntityFieldDto> getFieldById(UUID id) {
        return fieldRepository.findById(id)
                .map(fieldMapper::toDto);
    }

    /**
     * Create a new field for a virtual entity.
     *
     * @param fieldDto the field to create
     * @return a Mono emitting the created field
     */
    @Transactional
    public Mono<VirtualEntityFieldDto> createField(VirtualEntityFieldDto fieldDto) {
        return entityRepository.findById(fieldDto.getEntityId())
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Entity with ID " + fieldDto.getEntityId() + " not found")))
                .flatMap(entity -> {
                    return fieldRepository.findByEntityIdAndFieldKey(entity.getId(), fieldDto.getFieldKey())
                            .hasElement()
                            .flatMap(exists -> {
                                if (Boolean.TRUE.equals(exists)) {
                                    return Mono.error(new IllegalArgumentException("Field with key " + fieldDto.getFieldKey() + " already exists for entity"));
                                }

                                VirtualEntityField field = fieldMapper.toEntity(fieldDto);
                                field.setId(UUID.randomUUID());
                                field.setCreatedAt(LocalDateTime.now());
                                field.setUpdatedAt(LocalDateTime.now());

                                return fieldRepository.save(field)
                                        .map(fieldMapper::toDto)
                                        .doOnNext(savedField -> {
                                            // Invalidate the fields cache for this entity
                                            log.debug("Invalidating fields cache for entity ID: {}", entity.getId());
                                            entityCache.invalidateFields(entity.getId());
                                        });
                            });
                });
    }

    /**
     * Update an existing field.
     *
     * @param id       the ID of the field to update
     * @param fieldDto the updated field data
     * @return a Mono emitting the updated field
     */
    @Transactional
    public Mono<VirtualEntityFieldDto> updateField(UUID id, VirtualEntityFieldDto fieldDto) {
        return fieldRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Field with ID " + id + " not found")))
                .flatMap(existingField -> {
                    VirtualEntityField updatedField = fieldMapper.toEntity(fieldDto);
                    updatedField.setId(existingField.getId());
                    updatedField.setEntityId(existingField.getEntityId());
                    updatedField.setCreatedAt(existingField.getCreatedAt());
                    updatedField.setCreatedBy(existingField.getCreatedBy());
                    updatedField.setUpdatedAt(LocalDateTime.now());

                    return fieldRepository.save(updatedField)
                            .map(fieldMapper::toDto)
                            .doOnNext(savedField -> {
                                // Invalidate the fields cache for this entity
                                log.debug("Invalidating fields cache for entity ID: {}", existingField.getEntityId());
                                entityCache.invalidateFields(existingField.getEntityId());
                            });
                });
    }

    /**
     * Delete a field.
     *
     * @param id the ID of the field to delete
     * @return a Mono completing when the field is deleted
     */
    @Transactional
    public Mono<Void> deleteField(UUID id) {
        return fieldRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Field with ID " + id + " not found")))
                .flatMap(field -> {
                    UUID entityId = field.getEntityId();
                    return fieldRepository.deleteById(id)
                            .doOnSuccess(v -> {
                                // Invalidate the fields cache for this entity
                                log.debug("Invalidating fields cache for entity ID: {}", entityId);
                                entityCache.invalidateFields(entityId);
                            });
                });
    }

    /**
     * Delete all fields for a virtual entity.
     *
     * @param entityId the ID of the entity
     * @return a Mono completing when all fields are deleted
     */
    @Transactional
    public Mono<Void> deleteFieldsByEntityId(UUID entityId) {
        return fieldRepository.deleteByEntityId(entityId)
                .doOnSuccess(v -> {
                    // Invalidate the fields cache for this entity
                    log.debug("Invalidating fields cache for entity ID: {}", entityId);
                    entityCache.invalidateFields(entityId);
                });
    }
}
