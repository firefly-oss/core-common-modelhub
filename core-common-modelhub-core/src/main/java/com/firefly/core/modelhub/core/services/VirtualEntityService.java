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
import com.firefly.core.modelhub.interfaces.dtos.VirtualEntityDto;
import com.firefly.core.modelhub.interfaces.dtos.VirtualEntitySchemaDto;
import com.firefly.core.modelhub.core.mappers.VirtualEntityMapper;
import com.firefly.core.modelhub.models.entities.VirtualEntity;
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
 * Service for virtual entity operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VirtualEntityService {

    private final VirtualEntityRepository virtualEntityRepository;
    private final VirtualEntityFieldService virtualEntityFieldService;
    private final VirtualEntityRecordService virtualEntityRecordService;
    private final VirtualEntityMapper virtualEntityMapper;
    private final EntityDefinitionCache entityCache;

    /**
     * Get all virtual entities.
     *
     * @return a Flux emitting all virtual entities
     */
    public Flux<VirtualEntityDto> getAllEntities() {
        return virtualEntityRepository.findAll()
                .map(virtualEntityMapper::toDto);
    }

    /**
     * Get a virtual entity by ID.
     *
     * @param id the ID of the entity
     * @return a Mono emitting the found entity or empty if not found
     */
    public Mono<VirtualEntityDto> getEntityById(UUID id) {
        return entityCache.getEntityById(id, entityId -> 
            virtualEntityRepository.findById(entityId)
                .map(virtualEntityMapper::toDto)
        );
    }

    /**
     * Get a virtual entity by name.
     *
     * @param name the name of the entity
     * @return a Mono emitting the found entity or empty if not found
     */
    public Mono<VirtualEntityDto> getEntityByName(String name) {
        return entityCache.getEntityByName(name, entityName -> 
            virtualEntityRepository.findByName(entityName)
                .map(virtualEntityMapper::toDto)
        );
    }

    /**
     * Get the schema for a virtual entity.
     *
     * @param name the name of the entity
     * @return a Mono emitting the entity schema or empty if not found
     */
    public Mono<VirtualEntitySchemaDto> getEntitySchema(String name) {
        return entityCache.getEntityByName(name, entityName -> 
                virtualEntityRepository.findByNameAndActive(entityName, true)
                    .map(virtualEntityMapper::toDto)
            )
            .flatMap(entityDto -> {
                return virtualEntityFieldService.getFieldsByEntityId(entityDto.getId())
                        .collectList()
                        .map(fields -> {
                            return VirtualEntitySchemaDto.builder()
                                .entity(entityDto)
                                .fields(fields)
                                .build();
                        });
            });
    }

    /**
     * Create a new virtual entity.
     *
     * @param entityDto the entity to create
     * @return a Mono emitting the created entity
     */
    @Transactional
    public Mono<VirtualEntityDto> createEntity(VirtualEntityDto entityDto) {
        return virtualEntityRepository.existsByName(entityDto.getName())
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        return Mono.error(new IllegalArgumentException("Entity with name " + entityDto.getName() + " already exists"));
                    }

                    VirtualEntity entity = virtualEntityMapper.toEntity(entityDto);
                    entity.setId(UUID.randomUUID());
                    entity.setCreatedAt(LocalDateTime.now());
                    entity.setUpdatedAt(LocalDateTime.now());

                    return virtualEntityRepository.save(entity)
                            .map(virtualEntityMapper::toDto)
                            .doOnNext(savedEntity -> {
                                // Cache the newly created entity
                                log.debug("Caching newly created entity: {}", savedEntity.getName());
                                // No need to explicitly cache as it will be cached on first access
                            });
                });
    }

    /**
     * Update an existing virtual entity.
     *
     * @param id        the ID of the entity to update
     * @param entityDto the updated entity data
     * @return a Mono emitting the updated entity
     */
    @Transactional
    public Mono<VirtualEntityDto> updateEntity(UUID id, VirtualEntityDto entityDto) {
        return virtualEntityRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Entity with ID " + id + " not found")))
                .flatMap(existingEntity -> {
                    VirtualEntity updatedEntity = virtualEntityMapper.toEntity(entityDto);
                    updatedEntity.setId(existingEntity.getId());
                    updatedEntity.setCreatedAt(existingEntity.getCreatedAt());
                    updatedEntity.setCreatedBy(existingEntity.getCreatedBy());
                    updatedEntity.setUpdatedAt(LocalDateTime.now());

                    return virtualEntityRepository.save(updatedEntity)
                            .map(virtualEntityMapper::toDto)
                            .doOnNext(savedEntity -> {
                                // Invalidate the cache for the updated entity
                                log.debug("Invalidating cache for updated entity: {}", savedEntity.getName());
                                entityCache.invalidateEntity(id);
                            });
                });
    }

    /**
     * Delete a virtual entity.
     *
     * @param id the ID of the entity to delete
     * @return a Mono completing when the entity is deleted
     */
    @Transactional
    public Mono<Void> deleteEntity(UUID id) {
        return virtualEntityRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Entity with ID " + id + " not found")))
                .flatMap(entity -> {
                    // First delete all records, then fields, then the entity
                    return virtualEntityRecordService.deleteRecordsByEntityId(id)
                            .then(virtualEntityFieldService.deleteFieldsByEntityId(id))
                            .then(virtualEntityRepository.deleteById(id))
                            .doOnSuccess(v -> {
                                // Invalidate the cache for the deleted entity
                                log.debug("Invalidating cache for deleted entity with ID: {}", id);
                                entityCache.invalidateEntity(id);
                            });
                });
    }
}
