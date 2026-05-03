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


package com.firefly.core.modelhub.core.cache;

import com.firefly.core.modelhub.interfaces.dtos.VirtualEntityDto;
import com.firefly.core.modelhub.interfaces.dtos.VirtualEntityFieldDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
public class EntityDefinitionCacheTest {

    @InjectMocks
    private EntityDefinitionCache cache;

    private UUID entityId;
    private VirtualEntityDto entity;
    private List<VirtualEntityFieldDto> fields;

    @BeforeEach
    void setUp() {
        cache = new EntityDefinitionCache();
        
        entityId = UUID.randomUUID();
        entity = VirtualEntityDto.builder()
                .id(entityId)
                .name("testEntity")
                .description("Test Entity")
                .active(true)
                .build();
                
        fields = Arrays.asList(
            VirtualEntityFieldDto.builder()
                .id(UUID.randomUUID())
                .entityId(entityId)
                .fieldKey("field1")
                .fieldType("string")
                .build(),
            VirtualEntityFieldDto.builder()
                .id(UUID.randomUUID())
                .entityId(entityId)
                .fieldKey("field2")
                .fieldType("number")
                .build()
        );
    }

    @Test
    void getEntityById_ShouldCacheAndReturnEntity() {
        // Given
        AtomicInteger loaderCallCount = new AtomicInteger(0);
        Function<UUID, Mono<VirtualEntityDto>> loader = id -> {
            loaderCallCount.incrementAndGet();
            return Mono.just(entity);
        };

        // When & Then - First call should use the loader
        StepVerifier.create(cache.getEntityById(entityId, loader))
                .expectNext(entity)
                .verifyComplete();
        assertEquals(1, loaderCallCount.get());

        // When & Then - Second call should use the cache
        StepVerifier.create(cache.getEntityById(entityId, loader))
                .expectNext(entity)
                .verifyComplete();
        assertEquals(1, loaderCallCount.get()); // Loader should not be called again
    }

    @Test
    void getEntityByName_ShouldCacheAndReturnEntity() {
        // Given
        AtomicInteger loaderCallCount = new AtomicInteger(0);
        Function<String, Mono<VirtualEntityDto>> loader = name -> {
            loaderCallCount.incrementAndGet();
            return Mono.just(entity);
        };

        // When & Then - First call should use the loader
        StepVerifier.create(cache.getEntityByName("testEntity", loader))
                .expectNext(entity)
                .verifyComplete();
        assertEquals(1, loaderCallCount.get());

        // When & Then - Second call should use the cache
        StepVerifier.create(cache.getEntityByName("testEntity", loader))
                .expectNext(entity)
                .verifyComplete();
        assertEquals(1, loaderCallCount.get()); // Loader should not be called again
    }

    @Test
    void getFieldsByEntityId_ShouldCacheAndReturnFields() {
        // Given
        AtomicInteger loaderCallCount = new AtomicInteger(0);
        Function<UUID, Flux<VirtualEntityFieldDto>> loader = id -> {
            loaderCallCount.incrementAndGet();
            return Flux.fromIterable(fields);
        };

        // When & Then - First call should use the loader
        StepVerifier.create(cache.getFieldsByEntityId(entityId, loader).collectList())
                .expectNext(fields)
                .verifyComplete();
        assertEquals(1, loaderCallCount.get());

        // When & Then - Second call should use the cache
        StepVerifier.create(cache.getFieldsByEntityId(entityId, loader).collectList())
                .expectNext(fields)
                .verifyComplete();
        assertEquals(1, loaderCallCount.get()); // Loader should not be called again
    }

    @Test
    void invalidateEntity_ShouldRemoveEntityFromCache() {
        // Given
        AtomicInteger loaderCallCount = new AtomicInteger(0);
        Function<UUID, Mono<VirtualEntityDto>> loader = id -> {
            loaderCallCount.incrementAndGet();
            return Mono.just(entity);
        };

        // First call to cache the entity
        StepVerifier.create(cache.getEntityById(entityId, loader))
                .expectNext(entity)
                .verifyComplete();
        assertEquals(1, loaderCallCount.get());

        // When - Invalidate the entity
        cache.invalidateEntity(entityId);

        // Then - Next call should use the loader again
        StepVerifier.create(cache.getEntityById(entityId, loader))
                .expectNext(entity)
                .verifyComplete();
        assertEquals(2, loaderCallCount.get()); // Loader should be called again
    }

    @Test
    void invalidateFields_ShouldRemoveFieldsFromCache() {
        // Given
        AtomicInteger loaderCallCount = new AtomicInteger(0);
        Function<UUID, Flux<VirtualEntityFieldDto>> loader = id -> {
            loaderCallCount.incrementAndGet();
            return Flux.fromIterable(fields);
        };

        // First call to cache the fields
        StepVerifier.create(cache.getFieldsByEntityId(entityId, loader).collectList())
                .expectNext(fields)
                .verifyComplete();
        assertEquals(1, loaderCallCount.get());

        // When - Invalidate the fields
        cache.invalidateFields(entityId);

        // Then - Next call should use the loader again
        StepVerifier.create(cache.getFieldsByEntityId(entityId, loader).collectList())
                .expectNext(fields)
                .verifyComplete();
        assertEquals(2, loaderCallCount.get()); // Loader should be called again
    }

    @Test
    void clearAll_ShouldRemoveAllEntriesFromCache() {
        // Given
        AtomicInteger entityLoaderCallCount = new AtomicInteger(0);
        AtomicInteger fieldsLoaderCallCount = new AtomicInteger(0);
        
        Function<UUID, Mono<VirtualEntityDto>> entityLoader = id -> {
            entityLoaderCallCount.incrementAndGet();
            return Mono.just(entity);
        };
        
        Function<UUID, Flux<VirtualEntityFieldDto>> fieldsLoader = id -> {
            fieldsLoaderCallCount.incrementAndGet();
            return Flux.fromIterable(fields);
        };

        // First calls to cache the entity and fields
        StepVerifier.create(cache.getEntityById(entityId, entityLoader))
                .expectNext(entity)
                .verifyComplete();
        StepVerifier.create(cache.getFieldsByEntityId(entityId, fieldsLoader).collectList())
                .expectNext(fields)
                .verifyComplete();
        assertEquals(1, entityLoaderCallCount.get());
        assertEquals(1, fieldsLoaderCallCount.get());

        // When - Clear all caches
        cache.clearAll();

        // Then - Next calls should use the loaders again
        StepVerifier.create(cache.getEntityById(entityId, entityLoader))
                .expectNext(entity)
                .verifyComplete();
        StepVerifier.create(cache.getFieldsByEntityId(entityId, fieldsLoader).collectList())
                .expectNext(fields)
                .verifyComplete();
        assertEquals(2, entityLoaderCallCount.get()); // Loader should be called again
        assertEquals(2, fieldsLoaderCallCount.get()); // Loader should be called again
    }
}