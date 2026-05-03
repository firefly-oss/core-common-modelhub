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


package com.firefly.core.modelhub.models.repositories;

import com.firefly.core.modelhub.models.entities.VirtualEntityRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Repository for VirtualEntityRecord operations.
 */
@Repository
public interface VirtualEntityRecordRepository extends R2dbcRepository<VirtualEntityRecord, UUID>, VirtualEntityRecordRepositoryCustom {

    /**
     * Find all records for a virtual entity.
     *
     * @param entityId the ID of the entity
     * @return a Flux emitting the found records
     */
    Flux<VirtualEntityRecord> findByEntityId(UUID entityId);

    /**
     * Find all records for a virtual entity with pagination.
     *
     * @param entityId the ID of the entity
     * @param pageable the pagination information
     * @return a Flux emitting the found records
     */
    Flux<VirtualEntityRecord> findByEntityId(UUID entityId, Pageable pageable);

    /**
     * Count the number of records for a virtual entity.
     *
     * @param entityId the ID of the entity
     * @return a Mono emitting the count
     */
    Mono<Long> countByEntityId(UUID entityId);

    /**
     * Find records by entity ID and a JSON field value.
     *
     * @param entityId the ID of the entity
     * @param fieldKey the key of the field
     * @param value the value to search for
     * @return a Flux emitting the found records
     */
    @Query("SELECT * FROM virtual_entity_record WHERE entity_id = :entityId AND payload->>:fieldKey = :value")
    Flux<VirtualEntityRecord> findByEntityIdAndFieldValue(UUID entityId, String fieldKey, String value);

    /**
     * Delete all records for a virtual entity.
     *
     * @param entityId the ID of the entity
     * @return a Mono emitting the number of deleted records
     */
    Mono<Void> deleteByEntityId(UUID entityId);
}
