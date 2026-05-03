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

import com.firefly.core.modelhub.core.parsers.SqlQueryParser;
import com.firefly.core.modelhub.interfaces.dtos.QueryDto;
import com.firefly.core.modelhub.interfaces.dtos.VirtualEntityRecordDto;
import com.firefly.core.modelhub.core.mappers.VirtualEntityRecordMapper;
import com.firefly.core.modelhub.models.entities.VirtualEntityRecord;
import com.firefly.core.modelhub.models.repositories.VirtualEntityRecordRepository;
import com.firefly.core.modelhub.models.repositories.VirtualEntityRecordRepositoryCustomImpl.QueryBuildException;
import com.firefly.core.modelhub.models.repositories.VirtualEntityRecordRepositoryCustomImpl.QueryExecutionException;
import com.firefly.core.modelhub.models.repositories.VirtualEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Service for virtual entity record operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VirtualEntityRecordService {

    private final VirtualEntityRecordRepository recordRepository;
    private final VirtualEntityRepository entityRepository;
    private final VirtualEntityFieldService fieldService;
    private final VirtualEntityRecordMapper recordMapper;
    private final SqlQueryParser sqlQueryParser;

    /**
     * Get all records for a virtual entity.
     *
     * @param entityId the ID of the entity
     * @return a Flux emitting the records
     */
    public Flux<VirtualEntityRecordDto> getRecordsByEntityId(UUID entityId) {
        return recordRepository.findByEntityId(entityId)
                .map(recordMapper::toDto);
    }

    /**
     * Get all records for a virtual entity with pagination.
     *
     * @param entityId the ID of the entity
     * @param pageable the pagination information
     * @return a Flux emitting the records
     */
    public Flux<VirtualEntityRecordDto> getRecordsByEntityId(UUID entityId, Pageable pageable) {
        return recordRepository.findByEntityId(entityId, pageable)
                .map(recordMapper::toDto);
    }

    /**
     * Get a record by ID.
     *
     * @param id the ID of the record
     * @return a Mono emitting the record or empty if not found
     */
    public Mono<VirtualEntityRecordDto> getRecordById(UUID id) {
        return recordRepository.findById(id)
                .map(recordMapper::toDto);
    }

    /**
     * Create a new record for a virtual entity.
     *
     * @param recordDto the record to create
     * @return a Mono emitting the created record
     */
    @Transactional
    public Mono<VirtualEntityRecordDto> createRecord(VirtualEntityRecordDto recordDto) {
        return entityRepository.findById(recordDto.getEntityId())
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Entity with ID " + recordDto.getEntityId() + " not found")))
                .flatMap(entity -> {
                    // Validate the record against the entity schema
                    return validateRecord(recordDto)
                            .flatMap(valid -> {
                                VirtualEntityRecord record = recordMapper.toEntity(recordDto);
                                record.setId(UUID.randomUUID());
                                record.setCreatedAt(LocalDateTime.now());
                                record.setUpdatedAt(LocalDateTime.now());

                                return recordRepository.save(record)
                                        .map(recordMapper::toDto);
                            });
                });
    }

    /**
     * Update an existing record.
     *
     * @param id        the ID of the record to update
     * @param recordDto the updated record data
     * @return a Mono emitting the updated record
     */
    @Transactional
    public Mono<VirtualEntityRecordDto> updateRecord(UUID id, VirtualEntityRecordDto recordDto) {
        return recordRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Record with ID " + id + " not found")))
                .flatMap(existingRecord -> {
                    // Validate the record against the entity schema
                    return validateRecord(recordDto)
                            .flatMap(valid -> {
                                VirtualEntityRecord updatedRecord = recordMapper.toEntity(recordDto);
                                updatedRecord.setId(existingRecord.getId());
                                updatedRecord.setEntityId(existingRecord.getEntityId());
                                updatedRecord.setCreatedAt(existingRecord.getCreatedAt());
                                updatedRecord.setCreatedBy(existingRecord.getCreatedBy());
                                updatedRecord.setUpdatedAt(LocalDateTime.now());

                                return recordRepository.save(updatedRecord)
                                        .map(recordMapper::toDto);
                            });
                });
    }

    /**
     * Delete a record.
     *
     * @param id the ID of the record to delete
     * @return a Mono completing when the record is deleted
     */
    @Transactional
    public Mono<Void> deleteRecord(UUID id) {
        return recordRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Record with ID " + id + " not found")))
                .flatMap(record -> recordRepository.deleteById(id));
    }

    /**
     * Delete all records for a virtual entity.
     *
     * @param entityId the ID of the entity
     * @return a Mono completing when all records are deleted
     */
    @Transactional
    public Mono<Void> deleteRecordsByEntityId(UUID entityId) {
        return recordRepository.deleteByEntityId(entityId);
    }

    /**
     * Execute a query on virtual entity records.
     *
     * @param queryDto the query definition
     * @return a Flux emitting the matching records
     */
    public Flux<VirtualEntityRecordDto> executeQuery(QueryDto queryDto) {
        try {
            // Parse SQL-like query string if provided
            if (queryDto.getQueryString() != null && !queryDto.getQueryString().isEmpty()) {
                queryDto = sqlQueryParser.parse(queryDto);
            }

            return recordRepository.executeQuery(queryDto)
                    .map(recordMapper::toDto)
                    .onErrorResume(e -> {
                        if (e instanceof QueryBuildException || e instanceof QueryExecutionException) {
                            return Flux.error(e);
                        }
                        log.error("Unexpected error executing query: {}", e.getMessage(), e);
                        return Flux.error(new RuntimeException("Error executing query: " + e.getMessage(), e));
                    });
        } catch (Exception e) {
            log.error("Error preparing query: {}", e.getMessage(), e);
            return Flux.error(new RuntimeException("Error preparing query: " + e.getMessage(), e));
        }
    }

    /**
     * Count the number of records matching a query.
     *
     * @param queryDto the query definition
     * @return a Mono emitting the count
     */
    public Mono<Long> countQuery(QueryDto queryDto) {
        try {
            // Parse SQL-like query string if provided
            if (queryDto.getQueryString() != null && !queryDto.getQueryString().isEmpty()) {
                queryDto = sqlQueryParser.parse(queryDto);
            }

            return recordRepository.countQuery(queryDto)
                    .onErrorResume(e -> {
                        if (e instanceof QueryBuildException || e instanceof QueryExecutionException) {
                            return Mono.error(e);
                        }
                        log.error("Unexpected error counting query results: {}", e.getMessage(), e);
                        return Mono.error(new RuntimeException("Error counting query results: " + e.getMessage(), e));
                    });
        } catch (Exception e) {
            log.error("Error preparing count query: {}", e.getMessage(), e);
            return Mono.error(new RuntimeException("Error preparing count query: " + e.getMessage(), e));
        }
    }

    /**
     * Validate a record against the entity schema.
     *
     * @param recordDto the record to validate
     * @return a Mono completing when validation is successful, or an error if validation fails
     */
    private Mono<Boolean> validateRecord(VirtualEntityRecordDto recordDto) {
        return fieldService.getFieldsByEntityId(recordDto.getEntityId())
                .collectList()
                .flatMap(fields -> {
                    Map<String, Object> payload = recordDto.getPayload();

                    // Check required fields
                    for (var field : fields) {
                        String fieldKey = field.getFieldKey();
                        if (Boolean.TRUE.equals(field.getRequired()) && !payload.containsKey(fieldKey)) {
                            return Mono.error(new IllegalArgumentException("Required field '" + fieldKey + "' is missing"));
                        }
                    }

                    // Additional validation could be added here (type checking, etc.)

                    return Mono.just(true);
                });
    }
}
