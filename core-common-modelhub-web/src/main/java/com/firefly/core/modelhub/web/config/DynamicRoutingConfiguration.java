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


package com.firefly.core.modelhub.web.config;

import com.firefly.core.modelhub.core.services.VirtualEntityFieldService;
import com.firefly.core.modelhub.core.services.VirtualEntityRecordService;
import com.firefly.core.modelhub.core.services.VirtualEntityService;
import com.firefly.core.modelhub.core.validators.RecordValidator;
import com.firefly.core.modelhub.interfaces.dtos.QueryDto;
import com.firefly.core.modelhub.interfaces.dtos.VirtualEntityDto;
import com.firefly.core.modelhub.interfaces.dtos.VirtualEntityRecordDto;
import com.firefly.core.modelhub.web.controllers.VirtualEntityController;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Configuration for dynamic routing of entity API requests.
 * This class sets up routes that dynamically map to entity operations based on the entity name in the URL.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class DynamicRoutingConfiguration {

    private final VirtualEntityService entityService;
    private final VirtualEntityRecordService recordService;
    private final VirtualEntityFieldService fieldService;
    private final RecordValidator recordValidator;

    @Bean
    public RouterFunction<ServerResponse> dynamicEntityRoutes() {
        return RouterFunctions
                // GET /api/{entityName} - Get all records for an entity
                .route(RequestPredicates.GET("/api/{entityName}")
                        .and(this::isValidEntityName), this::getAllRecords)

                // GET /api/{entityName}/{id} - Get a record by ID
                .andRoute(RequestPredicates.GET("/api/{entityName}/{id}")
                        .and(this::isValidEntityName), this::getRecordById)

                // POST /api/{entityName} - Create a new record
                .andRoute(RequestPredicates.POST("/api/{entityName}")
                        .and(RequestPredicates.accept(MediaType.APPLICATION_JSON))
                        .and(this::isValidEntityName), this::createRecord)

                // PUT /api/{entityName}/{id} - Update a record
                .andRoute(RequestPredicates.PUT("/api/{entityName}/{id}")
                        .and(RequestPredicates.accept(MediaType.APPLICATION_JSON))
                        .and(this::isValidEntityName), this::updateRecord)

                // DELETE /api/{entityName}/{id} - Delete a record
                .andRoute(RequestPredicates.DELETE("/api/{entityName}/{id}")
                        .and(this::isValidEntityName), this::deleteRecord)

                // POST /api/{entityName}/query - Query records
                .andRoute(RequestPredicates.POST("/api/{entityName}/query")
                        .and(RequestPredicates.accept(MediaType.APPLICATION_JSON))
                        .and(this::isValidEntityName), this::queryRecords);
    }

    /**
     * Predicate to check if the entity name in the path is valid (exists in the system).
     * 
     * Note: This method uses a synchronous check for reserved paths, but delegates the
     * actual entity existence check to the handler methods to maintain reactive flow.
     */
    private boolean isValidEntityName(ServerRequest request) {
        String entityName = request.pathVariable("entityName");

        // Skip routing if the path starts with "api/v1" or "api/dynamic" as these are handled by controllers
        if (entityName.startsWith("v1") || entityName.equals("dynamic")) {
            return false;
        }

        // We'll return true here and let the handler methods handle the entity existence check
        // This avoids blocking in the predicate
        return true;
    }

    /**
     * Handler for GET /api/{entityName} - Get all records for an entity.
     */
    private Mono<ServerResponse> getAllRecords(ServerRequest request) {
        String entityName = request.pathVariable("entityName");
        int page = request.queryParam("page").map(Integer::parseInt).orElse(0);
        int size = request.queryParam("size").map(Integer::parseInt).orElse(20);

        return entityService.getEntityByName(entityName)
                .switchIfEmpty(Mono.error(new VirtualEntityController.ResourceNotFoundException("Entity not found with name: " + entityName)))
                .flatMap(entity -> {
                    Flux<VirtualEntityRecordDto> records = recordService.getRecordsByEntityId(entity.getId(), PageRequest.of(page, size));

                    return records.collectList()
                            .flatMap(recordList -> {
                                Map<String, Object> response = new HashMap<>();
                                response.put("entityName", entityName);
                                response.put("page", page);
                                response.put("size", size);
                                response.put("records", recordList);

                                return ServerResponse.ok()
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .bodyValue(response);
                            });
                })
                .onErrorResume(VirtualEntityController.ResourceNotFoundException.class, 
                        e -> ServerResponse.status(HttpStatus.NOT_FOUND).bodyValue(Map.of("error", "Entity not found")));
    }

    /**
     * Handler for GET /api/{entityName}/{id} - Get a record by ID.
     */
    private Mono<ServerResponse> getRecordById(ServerRequest request) {
        String entityName = request.pathVariable("entityName");
        UUID id;
        try {
            id = UUID.fromString(request.pathVariable("id"));
        } catch (IllegalArgumentException e) {
            return ServerResponse.badRequest().bodyValue(Map.of("error", "Invalid Long format"));
        }

        return entityService.getEntityByName(entityName)
                .switchIfEmpty(Mono.error(new VirtualEntityController.ResourceNotFoundException("Entity not found with name: " + entityName)))
                .flatMap(entity -> recordService.getRecordById(id)
                        .switchIfEmpty(Mono.error(new VirtualEntityController.ResourceNotFoundException("Record not found with ID: " + id)))
                        .filter(record -> record.getEntityId().equals(entity.getId()))
                        .switchIfEmpty(Mono.error(new VirtualEntityController.ResourceNotFoundException("Record with ID: " + id + " does not belong to entity: " + entityName)))
                        .flatMap(record -> ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(record)))
                .onErrorResume(VirtualEntityController.ResourceNotFoundException.class, 
                        e -> ServerResponse.status(HttpStatus.NOT_FOUND).bodyValue(Map.of("error", "Entity or record not found")));
    }

    /**
     * Handler for POST /api/{entityName} - Create a new record.
     */
    private Mono<ServerResponse> createRecord(ServerRequest request) {
        String entityName = request.pathVariable("entityName");

        return entityService.getEntityByName(entityName)
                .switchIfEmpty(Mono.error(new VirtualEntityController.ResourceNotFoundException("Entity not found with name: " + entityName)))
                .flatMap(entity -> request.bodyToMono(Map.class)
                        .flatMap(recordData -> {
                            // First validate the record
                            return recordValidator.validateRecord(recordData, fieldService.getFieldsByEntityId(entity.getId()))
                                    .then(Mono.defer(() -> {
                                        // Then create the record if validation passes
                                        VirtualEntityRecordDto recordDto = new VirtualEntityRecordDto();
                                        recordDto.setEntityId(entity.getId());
                                        recordDto.setPayload(recordData);

                                        return recordService.createRecord(recordDto)
                                                .flatMap(record -> ServerResponse.status(HttpStatus.CREATED)
                                                        .contentType(MediaType.APPLICATION_JSON)
                                                        .bodyValue(record));
                                    }));
                        }))
                .onErrorResume(VirtualEntityController.ResourceNotFoundException.class, 
                        e -> ServerResponse.status(HttpStatus.NOT_FOUND).bodyValue(Map.of("error", "Entity not found")));
    }

    /**
     * Handler for PUT /api/{entityName}/{id} - Update a record.
     */
    private Mono<ServerResponse> updateRecord(ServerRequest request) {
        String entityName = request.pathVariable("entityName");
        UUID id;
        try {
            id = UUID.fromString(request.pathVariable("id"));
        } catch (IllegalArgumentException e) {
            return ServerResponse.badRequest().bodyValue(Map.of("error", "Invalid Long format"));
        }

        return entityService.getEntityByName(entityName)
                .switchIfEmpty(Mono.error(new VirtualEntityController.ResourceNotFoundException("Entity not found with name: " + entityName)))
                .flatMap(entity -> recordService.getRecordById(id)
                        .switchIfEmpty(Mono.error(new VirtualEntityController.ResourceNotFoundException("Record not found with ID: " + id)))
                        .filter(record -> record.getEntityId().equals(entity.getId()))
                        .switchIfEmpty(Mono.error(new VirtualEntityController.ResourceNotFoundException("Record with ID: " + id + " does not belong to entity: " + entityName)))
                        .flatMap(existingRecord -> request.bodyToMono(Map.class)
                                .flatMap(recordData -> {
                                    VirtualEntityRecordDto recordDto = new VirtualEntityRecordDto();
                                    recordDto.setId(id);
                                    recordDto.setEntityId(entity.getId());
                                    recordDto.setPayload(recordData);

                                    return recordService.updateRecord(id, recordDto)
                                            .flatMap(record -> ServerResponse.ok()
                                                    .contentType(MediaType.APPLICATION_JSON)
                                                    .bodyValue(record));
                                })))
                .onErrorResume(VirtualEntityController.ResourceNotFoundException.class, 
                        e -> ServerResponse.status(HttpStatus.NOT_FOUND).bodyValue(Map.of("error", "Entity or record not found")));
    }

    /**
     * Handler for DELETE /api/{entityName}/{id} - Delete a record.
     */
    private Mono<ServerResponse> deleteRecord(ServerRequest request) {
        String entityName = request.pathVariable("entityName");
        UUID id;
        try {
            id = UUID.fromString(request.pathVariable("id"));
        } catch (IllegalArgumentException e) {
            return ServerResponse.badRequest().bodyValue(Map.of("error", "Invalid Long format"));
        }

        return entityService.getEntityByName(entityName)
                .switchIfEmpty(Mono.error(new VirtualEntityController.ResourceNotFoundException("Entity not found with name: " + entityName)))
                .flatMap(entity -> recordService.getRecordById(id)
                        .switchIfEmpty(Mono.error(new VirtualEntityController.ResourceNotFoundException("Record not found with ID: " + id)))
                        .filter(record -> record.getEntityId().equals(entity.getId()))
                        .switchIfEmpty(Mono.error(new VirtualEntityController.ResourceNotFoundException("Record with ID: " + id + " does not belong to entity: " + entityName)))
                        .flatMap(existingRecord -> recordService.deleteRecord(id)
                                .then(ServerResponse.noContent().build())))
                .onErrorResume(VirtualEntityController.ResourceNotFoundException.class, 
                        e -> ServerResponse.status(HttpStatus.NOT_FOUND).bodyValue(Map.of("error", "Entity or record not found")));
    }

    /**
     * Handler for POST /api/{entityName}/query - Query records.
     */
    private Mono<ServerResponse> queryRecords(ServerRequest request) {
        String entityName = request.pathVariable("entityName");

        return entityService.getEntityByName(entityName)
                .switchIfEmpty(Mono.error(new VirtualEntityController.ResourceNotFoundException("Entity not found with name: " + entityName)))
                .flatMap(entity -> request.bodyToMono(QueryDto.class)
                        .flatMap(queryDto -> {
                            // Set the entity ID in the query
                            queryDto.setEntityId(entity.getId());

                            return recordService.countQuery(queryDto)
                                    .flatMap(count -> {
                                        Flux<VirtualEntityRecordDto> records = recordService.executeQuery(queryDto);

                                        return records.collectList()
                                                .flatMap(recordList -> {
                                                    Map<String, Object> response = new HashMap<>();
                                                    response.put("entityName", entityName);
                                                    response.put("totalCount", count);
                                                    response.put("page", queryDto.getPage());
                                                    response.put("size", queryDto.getSize());
                                                    response.put("records", recordList);

                                                    return ServerResponse.ok()
                                                            .contentType(MediaType.APPLICATION_JSON)
                                                            .bodyValue(response);
                                                });
                                    });
                        }))
                .onErrorResume(VirtualEntityController.ResourceNotFoundException.class, 
                        e -> ServerResponse.status(HttpStatus.NOT_FOUND).bodyValue(Map.of("error", "Entity not found")));
    }
}
