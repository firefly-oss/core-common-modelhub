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


package com.firefly.core.modelhub.web.controllers;

import com.firefly.core.modelhub.core.services.VirtualEntityFieldService;
import com.firefly.core.modelhub.core.services.VirtualEntityRecordService;
import com.firefly.core.modelhub.core.services.VirtualEntityService;
import com.firefly.core.modelhub.core.validators.RecordValidator;
import com.firefly.core.modelhub.interfaces.dtos.QueryDto;
import com.firefly.core.modelhub.interfaces.dtos.VirtualEntityRecordDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for dynamic entity API operations.
 * This controller provides RESTful endpoints for each virtual entity in the system.
 */
@RestController
@RequestMapping("/api/dynamic")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Dynamic Entity APIs", description = "Dynamic API endpoints for virtual entities")
public class DynamicEntityController {

    private final VirtualEntityService entityService;
    private final VirtualEntityRecordService recordService;
    private final VirtualEntityFieldService fieldService;
    private final RecordValidator recordValidator;

    @GetMapping(value = "/{entityName}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get all records for an entity by name",
            description = "Returns a list of all records for a specific virtual entity identified by name",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successful operation"),
                    @ApiResponse(responseCode = "404", description = "Entity not found", content = @Content)
            }
    )
    public Mono<ResponseEntity<Object>> getAllRecordsByEntityName(
            @Parameter(description = "Name of the entity to retrieve records for", required = true)
            @PathVariable String entityName,
            @Parameter(description = "Page number (zero-based)")
            @RequestParam(required = false, defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(required = false, defaultValue = "20") int size) {

        return entityService.getEntityByName(entityName)
                .switchIfEmpty(Mono.error(new VirtualEntityController.ResourceNotFoundException("Entity not found with name: " + entityName)))
                .flatMap(entity -> {
                    Flux<VirtualEntityRecordDto> records = recordService.getRecordsByEntityId(entity.getId(), PageRequest.of(page, size));
                    return records.collectList()
                            .map(recordList -> {
                                Map<String, Object> response = new HashMap<>();
                                response.put("entityName", entityName);
                                response.put("page", page);
                                response.put("size", size);
                                response.put("records", recordList);
                                return ResponseEntity.ok(response);
                            });
                });
    }

    @GetMapping(value = "/{entityName}/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get a record by ID for a specific entity",
            description = "Returns a virtual entity record by its ID for a specific entity identified by name",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successful operation"),
                    @ApiResponse(responseCode = "404", description = "Entity or record not found", content = @Content)
            }
    )
    public Mono<ResponseEntity<VirtualEntityRecordDto>> getRecordByIdForEntity(
            @Parameter(description = "Name of the entity", required = true)
            @PathVariable String entityName,
            @Parameter(description = "ID of the record to retrieve", required = true)
            @PathVariable UUID id) {

        return entityService.getEntityByName(entityName)
                .switchIfEmpty(Mono.error(new VirtualEntityController.ResourceNotFoundException("Entity not found with name: " + entityName)))
                .flatMap(entity -> recordService.getRecordById(id)
                        .switchIfEmpty(Mono.error(new VirtualEntityController.ResourceNotFoundException("Record not found with ID: " + id)))
                        .filter(record -> record.getEntityId().equals(entity.getId()))
                        .switchIfEmpty(Mono.error(new VirtualEntityController.ResourceNotFoundException("Record with ID: " + id + " does not belong to entity: " + entityName)))
                        .map(ResponseEntity::ok));
    }

    @PostMapping(
            value = "/{entityName}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "Create a new record for a specific entity",
            description = "Creates a new record for a virtual entity identified by name",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Record created successfully"),
                    @ApiResponse(responseCode = "400", description = "Invalid input or validation error", content = @Content),
                    @ApiResponse(responseCode = "404", description = "Entity not found", content = @Content)
            }
    )
    public Mono<ResponseEntity<VirtualEntityRecordDto>> createRecordForEntity(
            @Parameter(description = "Name of the entity", required = true)
            @PathVariable String entityName,
            @Parameter(description = "Record to create", required = true, schema = @Schema(implementation = VirtualEntityRecordDto.class))
            @Valid @RequestBody Map<String, Object> recordData) {

        return entityService.getEntityByName(entityName)
                .switchIfEmpty(Mono.error(new VirtualEntityController.ResourceNotFoundException("Entity not found with name: " + entityName)))
                .flatMap(entity -> {
                    // Validate the record against the entity's field definitions
                    return recordValidator.validateRecord(recordData, fieldService.getFieldsByEntityId(entity.getId()))
                            .then(Mono.defer(() -> {
                                VirtualEntityRecordDto recordDto = new VirtualEntityRecordDto();
                                recordDto.setEntityId(entity.getId());
                                recordDto.setPayload(recordData);

                                return recordService.createRecord(recordDto)
                                        .map(record -> ResponseEntity.status(HttpStatus.CREATED).body(record));
                            }))
                            .onErrorResume(RecordValidator.ValidationException.class, ex -> {
                                Map<String, Object> errorResponse = new HashMap<>();
                                errorResponse.put("message", ex.getMessage());
                                errorResponse.put("errors", ex.getErrors());
                                return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex));
                            });
                });
    }

    @PutMapping(
            value = "/{entityName}/{id}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "Update a record for a specific entity",
            description = "Updates an existing record for a virtual entity identified by name",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Record updated successfully"),
                    @ApiResponse(responseCode = "400", description = "Invalid input or validation error", content = @Content),
                    @ApiResponse(responseCode = "404", description = "Entity or record not found", content = @Content)
            }
    )
    public Mono<ResponseEntity<VirtualEntityRecordDto>> updateRecordForEntity(
            @Parameter(description = "Name of the entity", required = true)
            @PathVariable String entityName,
            @Parameter(description = "ID of the record to update", required = true)
            @PathVariable UUID id,
            @Parameter(description = "Updated record data", required = true)
            @Valid @RequestBody Map<String, Object> recordData) {

        return entityService.getEntityByName(entityName)
                .switchIfEmpty(Mono.error(new VirtualEntityController.ResourceNotFoundException("Entity not found with name: " + entityName)))
                .flatMap(entity -> recordService.getRecordById(id)
                        .switchIfEmpty(Mono.error(new VirtualEntityController.ResourceNotFoundException("Record not found with ID: " + id)))
                        .filter(record -> record.getEntityId().equals(entity.getId()))
                        .switchIfEmpty(Mono.error(new VirtualEntityController.ResourceNotFoundException("Record with ID: " + id + " does not belong to entity: " + entityName)))
                        .flatMap(existingRecord -> {
                            // Validate the record against the entity's field definitions
                            return recordValidator.validateRecord(recordData, fieldService.getFieldsByEntityId(entity.getId()))
                                    .then(Mono.defer(() -> {
                                        VirtualEntityRecordDto recordDto = new VirtualEntityRecordDto();
                                        recordDto.setId(id);
                                        recordDto.setEntityId(entity.getId());
                                        recordDto.setPayload(recordData);

                                        return recordService.updateRecord(id, recordDto)
                                                .map(ResponseEntity::ok);
                                    }))
                                    .onErrorResume(RecordValidator.ValidationException.class, ex -> {
                                        Map<String, Object> errorResponse = new HashMap<>();
                                        errorResponse.put("message", ex.getMessage());
                                        errorResponse.put("errors", ex.getErrors());
                                        return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex));
                                    });
                        }));
    }

    @DeleteMapping("/{entityName}/{id}")
    @Operation(
            summary = "Delete a record for a specific entity",
            description = "Deletes a record for a virtual entity identified by name",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Record deleted successfully"),
                    @ApiResponse(responseCode = "404", description = "Entity or record not found", content = @Content)
            }
    )
    public Mono<ResponseEntity<Void>> deleteRecordForEntity(
            @Parameter(description = "Name of the entity", required = true)
            @PathVariable String entityName,
            @Parameter(description = "ID of the record to delete", required = true)
            @PathVariable UUID id) {

        return entityService.getEntityByName(entityName)
                .switchIfEmpty(Mono.error(new VirtualEntityController.ResourceNotFoundException("Entity not found with name: " + entityName)))
                .flatMap(entity -> recordService.getRecordById(id)
                        .switchIfEmpty(Mono.error(new VirtualEntityController.ResourceNotFoundException("Record not found with ID: " + id)))
                        .filter(record -> record.getEntityId().equals(entity.getId()))
                        .switchIfEmpty(Mono.error(new VirtualEntityController.ResourceNotFoundException("Record with ID: " + id + " does not belong to entity: " + entityName)))
                        .flatMap(existingRecord -> recordService.deleteRecord(id)
                                .then(Mono.just(ResponseEntity.noContent().<Void>build()))));
    }

    @PostMapping(
            value = "/{entityName}/query",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "Query records for a specific entity",
            description = "Executes a query against records for a virtual entity identified by name",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Query executed successfully"),
                    @ApiResponse(responseCode = "400", description = "Invalid query", content = @Content),
                    @ApiResponse(responseCode = "404", description = "Entity not found", content = @Content)
            }
    )
    public Mono<ResponseEntity<Map<String, Object>>> queryRecordsForEntity(
            @Parameter(description = "Name of the entity", required = true)
            @PathVariable String entityName,
            @Parameter(description = "Query definition", required = true, schema = @Schema(implementation = QueryDto.class))
            @Valid @RequestBody QueryDto queryDto) {

        return entityService.getEntityByName(entityName)
                .switchIfEmpty(Mono.error(new VirtualEntityController.ResourceNotFoundException("Entity not found with name: " + entityName)))
                .flatMap(entity -> {
                    // Set the entity ID in the query
                    queryDto.setEntityId(entity.getId());

                    return recordService.countQuery(queryDto)
                            .flatMap(count -> {
                                Flux<VirtualEntityRecordDto> records = recordService.executeQuery(queryDto);

                                return records.collectList()
                                        .map(recordList -> {
                                            Map<String, Object> response = new HashMap<>();
                                            response.put("entityName", entityName);
                                            response.put("totalCount", count);
                                            response.put("page", queryDto.getPage());
                                            response.put("size", queryDto.getSize());
                                            response.put("records", recordList);

                                            return ResponseEntity.ok(response);
                                        });
                            });
                });
    }
}
