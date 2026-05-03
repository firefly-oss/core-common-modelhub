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

import com.firefly.core.modelhub.core.services.VirtualEntityService;
import com.firefly.core.modelhub.interfaces.dtos.VirtualEntityDto;
import com.firefly.core.modelhub.interfaces.dtos.VirtualEntitySchemaDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.validation.Valid;
import java.util.UUID;

/**
 * REST controller for virtual entity operations.
 */
@RestController
@RequestMapping("/api/v1/entities")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Virtual Entities", description = "API for managing virtual entity definitions")
public class VirtualEntityController {

    private final VirtualEntityService entityService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get all virtual entities",
            description = "Returns a list of all virtual entity definitions",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successful operation")
            }
    )
    public ResponseEntity<Flux<VirtualEntityDto>> getAllEntities() {
        return ResponseEntity.ok(entityService.getAllEntities());
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get a virtual entity by ID",
            description = "Returns a virtual entity definition by its ID",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successful operation"),
                    @ApiResponse(responseCode = "404", description = "Entity not found", content = @Content)
            }
    )
    public Mono<ResponseEntity<VirtualEntityDto>> getEntityById(
            @Parameter(description = "ID of the entity to retrieve", required = true)
            @PathVariable UUID id) {
        return entityService.getEntityById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Entity not found with ID: " + id)))
                .map(ResponseEntity::ok);
    }

    @GetMapping(value = "/name/{name}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get a virtual entity by name",
            description = "Returns a virtual entity definition by its name",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successful operation"),
                    @ApiResponse(responseCode = "404", description = "Entity not found", content = @Content)
            }
    )
    public Mono<ResponseEntity<VirtualEntityDto>> getEntityByName(
            @Parameter(description = "Name of the entity to retrieve", required = true)
            @PathVariable String name) {
        return entityService.getEntityByName(name)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Entity not found with name: " + name)))
                .map(ResponseEntity::ok);
    }

    @GetMapping(value = "/{name}/schema", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get the schema for a virtual entity",
            description = "Returns the complete schema (entity definition and fields) for a virtual entity",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successful operation"),
                    @ApiResponse(responseCode = "404", description = "Entity not found", content = @Content)
            }
    )
    public Mono<ResponseEntity<VirtualEntitySchemaDto>> getEntitySchema(
            @Parameter(description = "Name of the entity to retrieve the schema for", required = true)
            @PathVariable String name) {
        return entityService.getEntitySchema(name)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Entity not found with name: " + name)))
                .map(ResponseEntity::ok);
    }

    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "Create a new virtual entity",
            description = "Creates a new virtual entity definition",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Entity created successfully"),
                    @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content)
            }
    )
    public Mono<ResponseEntity<VirtualEntityDto>> createEntity(
            @Parameter(description = "Entity to create", required = true, schema = @Schema(implementation = VirtualEntityDto.class))
            @Valid @RequestBody VirtualEntityDto entityDto) {
        return entityService.createEntity(entityDto)
                .map(entity -> ResponseEntity.status(HttpStatus.CREATED).body(entity));
    }

    @PutMapping(
            value = "/{id}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "Update a virtual entity",
            description = "Updates an existing virtual entity definition",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Entity updated successfully"),
                    @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content),
                    @ApiResponse(responseCode = "404", description = "Entity not found", content = @Content)
            }
    )
    public Mono<ResponseEntity<VirtualEntityDto>> updateEntity(
            @Parameter(description = "ID of the entity to update", required = true)
            @PathVariable UUID id,
            @Parameter(description = "Updated entity data", required = true, schema = @Schema(implementation = VirtualEntityDto.class))
            @Valid @RequestBody VirtualEntityDto entityDto) {
        return entityService.updateEntity(id, entityDto)
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete a virtual entity",
            description = "Deletes a virtual entity definition and all its fields and records",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Entity deleted successfully"),
                    @ApiResponse(responseCode = "404", description = "Entity not found", content = @Content)
            }
    )
    public Mono<ResponseEntity<Void>> deleteEntity(
            @Parameter(description = "ID of the entity to delete", required = true)
            @PathVariable UUID id) {
        return entityService.deleteEntity(id)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }

    /**
     * Exception handler for resource not found exceptions.
     */
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class ResourceNotFoundException extends RuntimeException {
        public ResourceNotFoundException(String message) {
            super(message);
        }
    }
}
