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

import com.firefly.core.modelhub.interfaces.dtos.QueryDto;
import com.firefly.core.modelhub.models.entities.VirtualEntityRecord;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Custom repository interface for dynamic queries on VirtualEntityRecord.
 */
public interface VirtualEntityRecordRepositoryCustom {

    /**
     * Execute a dynamic query based on the provided query DTO.
     *
     * @param queryDto the query definition
     * @return a Flux emitting the matching records
     */
    Flux<VirtualEntityRecord> executeQuery(QueryDto queryDto);

    /**
     * Count the number of records matching a query.
     *
     * @param queryDto the query definition
     * @return a Mono emitting the count
     */
    Mono<Long> countQuery(QueryDto queryDto);
}