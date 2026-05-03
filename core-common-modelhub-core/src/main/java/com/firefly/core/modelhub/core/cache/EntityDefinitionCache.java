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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Cache for entity definitions and fields to improve performance.
 * This cache stores entity definitions and their fields to reduce database queries.
 */
@Component
@Slf4j
public class EntityDefinitionCache {

    // Cache TTL in seconds
    private static final long CACHE_TTL_SECONDS = 300; // 5 minutes

    // Cache for entities by ID
    private final Map<UUID, CacheEntry<VirtualEntityDto>> entitiesById = new ConcurrentHashMap<>();
    
    // Cache for entities by name
    private final Map<String, CacheEntry<VirtualEntityDto>> entitiesByName = new ConcurrentHashMap<>();
    
    // Cache for entity fields by entity ID
    private final Map<UUID, CacheEntry<List<VirtualEntityFieldDto>>> fieldsByEntityId = new ConcurrentHashMap<>();

    /**
     * Gets an entity by ID from the cache or the provided loader function.
     *
     * @param id The entity ID
     * @param loader The function to load the entity if not in cache
     * @return A Mono with the entity
     */
    public Mono<VirtualEntityDto> getEntityById(UUID id, Function<UUID, Mono<VirtualEntityDto>> loader) {
        CacheEntry<VirtualEntityDto> entry = entitiesById.get(id);
        
        if (entry != null && !entry.isExpired()) {
            log.debug("Cache hit for entity ID: {}", id);
            return Mono.just(entry.getValue());
        }
        
        log.debug("Cache miss for entity ID: {}, loading from database", id);
        return loader.apply(id)
                .doOnNext(entity -> {
                    if (entity != null) {
                        cacheEntity(entity);
                    }
                });
    }

    /**
     * Gets an entity by name from the cache or the provided loader function.
     *
     * @param name The entity name
     * @param loader The function to load the entity if not in cache
     * @return A Mono with the entity
     */
    public Mono<VirtualEntityDto> getEntityByName(String name, Function<String, Mono<VirtualEntityDto>> loader) {
        CacheEntry<VirtualEntityDto> entry = entitiesByName.get(name);
        
        if (entry != null && !entry.isExpired()) {
            log.debug("Cache hit for entity name: {}", name);
            return Mono.just(entry.getValue());
        }
        
        log.debug("Cache miss for entity name: {}, loading from database", name);
        return loader.apply(name)
                .doOnNext(entity -> {
                    if (entity != null) {
                        cacheEntity(entity);
                    }
                });
    }

    /**
     * Gets fields for an entity from the cache or the provided loader function.
     *
     * @param entityId The entity ID
     * @param loader The function to load the fields if not in cache
     * @return A Flux with the fields
     */
    public Flux<VirtualEntityFieldDto> getFieldsByEntityId(UUID entityId, Function<UUID, Flux<VirtualEntityFieldDto>> loader) {
        CacheEntry<List<VirtualEntityFieldDto>> entry = fieldsByEntityId.get(entityId);
        
        if (entry != null && !entry.isExpired()) {
            log.debug("Cache hit for fields of entity ID: {}", entityId);
            return Flux.fromIterable(entry.getValue());
        }
        
        log.debug("Cache miss for fields of entity ID: {}, loading from database", entityId);
        return loader.apply(entityId)
                .collectList()
                .doOnNext(fields -> {
                    if (fields != null && !fields.isEmpty()) {
                        fieldsByEntityId.put(entityId, new CacheEntry<>(fields));
                    }
                })
                .flatMapMany(Flux::fromIterable);
    }

    /**
     * Caches an entity in both ID and name caches.
     *
     * @param entity The entity to cache
     */
    private void cacheEntity(VirtualEntityDto entity) {
        entitiesById.put(entity.getId(), new CacheEntry<>(entity));
        entitiesByName.put(entity.getName(), new CacheEntry<>(entity));
    }

    /**
     * Invalidates the cache for an entity and its fields.
     *
     * @param entityId The entity ID
     */
    public void invalidateEntity(UUID entityId) {
        CacheEntry<VirtualEntityDto> entry = entitiesById.remove(entityId);
        if (entry != null && entry.getValue() != null) {
            entitiesByName.remove(entry.getValue().getName());
        }
        fieldsByEntityId.remove(entityId);
        log.debug("Cache invalidated for entity ID: {}", entityId);
    }

    /**
     * Invalidates the cache for entity fields.
     *
     * @param entityId The entity ID
     */
    public void invalidateFields(UUID entityId) {
        fieldsByEntityId.remove(entityId);
        log.debug("Cache invalidated for fields of entity ID: {}", entityId);
    }

    /**
     * Clears all caches.
     */
    public void clearAll() {
        entitiesById.clear();
        entitiesByName.clear();
        fieldsByEntityId.clear();
        log.debug("All caches cleared");
    }

    /**
     * Inner class representing a cache entry with expiration.
     *
     * @param <T> The type of the cached value
     */
    private static class CacheEntry<T> {
        private final T value;
        private final long expirationTime;

        public CacheEntry(T value) {
            this.value = value;
            this.expirationTime = System.currentTimeMillis() + (CACHE_TTL_SECONDS * 1000);
        }

        public T getValue() {
            return value;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expirationTime;
        }
    }
}