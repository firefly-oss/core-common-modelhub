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
import com.firefly.core.modelhub.interfaces.dtos.VirtualEntityFieldDto;
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
 * REST controller for virtual entity field operations.
 */
@RestController
@RequestMapping("/api/v1/fields")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Virtual Entity Fields", description = "API for managing virtual entity field definitions")
public class VirtualEntityFieldController {

    private final VirtualEntityFieldService fieldService;

    @GetMapping(value = "/entity/{entityId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get all fields for an entity",
            description = "Returns a list of all fields defined for a specific virtual entity",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successful operation"),
                    @ApiResponse(responseCode = "404", description = "Entity not found", content = @Content)
            }
    )
    public ResponseEntity<Flux<VirtualEntityFieldDto>> getFieldsByEntityId(
            @Parameter(description = "ID of the entity to retrieve fields for", required = true)
            @PathVariable UUID entityId) {
        return ResponseEntity.ok(fieldService.getFieldsByEntityId(entityId));
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get a field by ID",
            description = "Returns a virtual entity field definition by its ID",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successful operation"),
                    @ApiResponse(responseCode = "404", description = "Field not found", content = @Content)
            }
    )
    public Mono<ResponseEntity<VirtualEntityFieldDto>> getFieldById(
            @Parameter(description = "ID of the field to retrieve", required = true)
            @PathVariable UUID id) {
        return fieldService.getFieldById(id)
                .switchIfEmpty(Mono.error(new VirtualEntityController.ResourceNotFoundException("Field not found with ID: " + id)))
                .map(ResponseEntity::ok);
    }

    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "Create a new field",
            description = "Creates a new field definition for a virtual entity",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Field created successfully"),
                    @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content),
                    @ApiResponse(responseCode = "404", description = "Entity not found", content = @Content)
            }
    )
    public Mono<ResponseEntity<VirtualEntityFieldDto>> createField(
            @Parameter(description = "Field to create", required = true, schema = @Schema(implementation = VirtualEntityFieldDto.class))
            @Valid @RequestBody VirtualEntityFieldDto fieldDto) {
        return fieldService.createField(fieldDto)
                .map(field -> ResponseEntity.status(HttpStatus.CREATED).body(field));
    }

    @PutMapping(
            value = "/{id}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "Update a field",
            description = "Updates an existing field definition",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Field updated successfully"),
                    @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content),
                    @ApiResponse(responseCode = "404", description = "Field not found", content = @Content)
            }
    )
    public Mono<ResponseEntity<VirtualEntityFieldDto>> updateField(
            @Parameter(description = "ID of the field to update", required = true)
            @PathVariable UUID id,
            @Parameter(description = "Updated field data", required = true, schema = @Schema(implementation = VirtualEntityFieldDto.class))
            @Valid @RequestBody VirtualEntityFieldDto fieldDto) {
        return fieldService.updateField(id, fieldDto)
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete a field",
            description = "Deletes a field definition",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Field deleted successfully"),
                    @ApiResponse(responseCode = "404", description = "Field not found", content = @Content)
            }
    )
    public Mono<ResponseEntity<Void>> deleteField(
            @Parameter(description = "ID of the field to delete", required = true)
            @PathVariable UUID id) {
        return fieldService.deleteField(id)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }
}
