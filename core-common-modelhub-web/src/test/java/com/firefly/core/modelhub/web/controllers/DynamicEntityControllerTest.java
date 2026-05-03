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
import com.firefly.core.modelhub.interfaces.dtos.VirtualEntityDto;
import com.firefly.core.modelhub.interfaces.dtos.VirtualEntityRecordDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
public class DynamicEntityControllerTest {

    @Mock
    private VirtualEntityService entityService;

    @Mock
    private VirtualEntityRecordService recordService;

    @Mock
    private VirtualEntityFieldService fieldService;

    @Mock
    private RecordValidator recordValidator;

    @InjectMocks
    private DynamicEntityController dynamicEntityController;

    private WebTestClient webTestClient;
    private UUID entityId;
    private UUID recordId;
    private VirtualEntityDto entityDto;
    private VirtualEntityRecordDto recordDto;
    private Map<String, Object> recordPayload;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToController(dynamicEntityController).build();

        entityId = UUID.randomUUID();
        recordId = UUID.randomUUID();

        entityDto = VirtualEntityDto.builder()
                .id(entityId)
                .name("customer")
                .description("Customer entity")
                .active(true)
                .build();

        recordPayload = new HashMap<>();
        recordPayload.put("firstName", "John");
        recordPayload.put("lastName", "Doe");
        recordPayload.put("email", "john.doe@example.com");

        recordDto = VirtualEntityRecordDto.builder()
                .id(recordId)
                .entityId(entityId)
                .payload(recordPayload)
                .build();
    }

    @Test
    void getAllRecordsByEntityName_ShouldReturnRecords() {
        // Given
        String entityName = "customer";
        when(entityService.getEntityByName(entityName)).thenReturn(Mono.just(entityDto));
        when(recordService.getRecordsByEntityId(eq(entityId), any(PageRequest.class)))
                .thenReturn(Flux.just(recordDto));

        // When & Then
        webTestClient.get()
                .uri("/api/dynamic/{entityName}", entityName)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.entityName").isEqualTo(entityName)
                .jsonPath("$.records[0].id").isEqualTo(recordId.toString())
                .jsonPath("$.records[0].entityId").isEqualTo(entityId.toString())
                .jsonPath("$.records[0].payload.firstName").isEqualTo("John");
    }

    @Test
    void getAllRecordsByEntityName_WhenEntityNotFound_ShouldReturn404() {
        // Given
        String entityName = "nonexistent";
        when(entityService.getEntityByName(entityName)).thenReturn(Mono.empty());

        // When & Then
        webTestClient.get()
                .uri("/api/dynamic/{entityName}", entityName)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void getRecordByIdForEntity_ShouldReturnRecord() {
        // Given
        String entityName = "customer";
        when(entityService.getEntityByName(entityName)).thenReturn(Mono.just(entityDto));
        when(recordService.getRecordById(recordId)).thenReturn(Mono.just(recordDto));

        // When & Then
        webTestClient.get()
                .uri("/api/dynamic/{entityName}/{id}", entityName, recordId)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(recordId.toString())
                .jsonPath("$.entityId").isEqualTo(entityId.toString())
                .jsonPath("$.payload.firstName").isEqualTo("John");
    }

    @Test
    void getRecordByIdForEntity_WhenEntityNotFound_ShouldReturn404() {
        // Given
        String entityName = "nonexistent";
        when(entityService.getEntityByName(entityName)).thenReturn(Mono.empty());

        // When & Then
        webTestClient.get()
                .uri("/api/dynamic/{entityName}/{id}", entityName, recordId)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void getRecordByIdForEntity_WhenRecordNotFound_ShouldReturn404() {
        // Given
        String entityName = "customer";
        when(entityService.getEntityByName(entityName)).thenReturn(Mono.just(entityDto));
        when(recordService.getRecordById(recordId)).thenReturn(Mono.empty());

        // When & Then
        webTestClient.get()
                .uri("/api/dynamic/{entityName}/{id}", entityName, recordId)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void getRecordByIdForEntity_WhenRecordDoesNotBelongToEntity_ShouldReturn404() {
        // Given
        String entityName = "customer";
        UUID differentEntityId = UUID.randomUUID();
        VirtualEntityDto differentEntityDto = VirtualEntityDto.builder()
                .id(differentEntityId)
                .name(entityName)
                .build();

        when(entityService.getEntityByName(entityName)).thenReturn(Mono.just(differentEntityDto));
        when(recordService.getRecordById(recordId)).thenReturn(Mono.just(recordDto)); // Record belongs to entityId, not differentEntityId

        // When & Then
        webTestClient.get()
                .uri("/api/dynamic/{entityName}/{id}", entityName, recordId)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void createRecordForEntity_ShouldCreateAndReturnRecord() {
        // Given
        String entityName = "customer";
        when(entityService.getEntityByName(entityName)).thenReturn(Mono.just(entityDto));
        when(recordService.createRecord(any(VirtualEntityRecordDto.class))).thenReturn(Mono.just(recordDto));
        when(fieldService.getFieldsByEntityId(entityId)).thenReturn(Flux.empty());
        doReturn(Mono.empty()).when(recordValidator).validateRecord(any(), any());

        // When & Then
        webTestClient.post()
                .uri("/api/dynamic/{entityName}", entityName)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(recordPayload)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isEqualTo(recordId.toString())
                .jsonPath("$.entityId").isEqualTo(entityId.toString())
                .jsonPath("$.payload.firstName").isEqualTo("John");
    }

    @Test
    void createRecordForEntity_WhenEntityNotFound_ShouldReturn404() {
        // Given
        String entityName = "nonexistent";
        when(entityService.getEntityByName(entityName)).thenReturn(Mono.empty());

        // When & Then
        webTestClient.post()
                .uri("/api/dynamic/{entityName}", entityName)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(recordPayload)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void updateRecordForEntity_ShouldUpdateAndReturnRecord() {
        // Given
        String entityName = "customer";
        when(entityService.getEntityByName(entityName)).thenReturn(Mono.just(entityDto));
        when(recordService.getRecordById(recordId)).thenReturn(Mono.just(recordDto));
        when(recordService.updateRecord(eq(recordId), any(VirtualEntityRecordDto.class))).thenReturn(Mono.just(recordDto));
        when(fieldService.getFieldsByEntityId(entityId)).thenReturn(Flux.empty());
        doReturn(Mono.empty()).when(recordValidator).validateRecord(any(), any());

        // When & Then
        webTestClient.put()
                .uri("/api/dynamic/{entityName}/{id}", entityName, recordId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(recordPayload)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(recordId.toString())
                .jsonPath("$.entityId").isEqualTo(entityId.toString())
                .jsonPath("$.payload.firstName").isEqualTo("John");
    }

    @Test
    void updateRecordForEntity_WhenEntityNotFound_ShouldReturn404() {
        // Given
        String entityName = "nonexistent";
        when(entityService.getEntityByName(entityName)).thenReturn(Mono.empty());

        // When & Then
        webTestClient.put()
                .uri("/api/dynamic/{entityName}/{id}", entityName, recordId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(recordPayload)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void updateRecordForEntity_WhenRecordNotFound_ShouldReturn404() {
        // Given
        String entityName = "customer";
        when(entityService.getEntityByName(entityName)).thenReturn(Mono.just(entityDto));
        when(recordService.getRecordById(recordId)).thenReturn(Mono.empty());

        // When & Then
        webTestClient.put()
                .uri("/api/dynamic/{entityName}/{id}", entityName, recordId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(recordPayload)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void deleteRecordForEntity_ShouldDeleteRecord() {
        // Given
        String entityName = "customer";
        when(entityService.getEntityByName(entityName)).thenReturn(Mono.just(entityDto));
        when(recordService.getRecordById(recordId)).thenReturn(Mono.just(recordDto));
        when(recordService.deleteRecord(recordId)).thenReturn(Mono.empty());

        // When & Then
        webTestClient.delete()
                .uri("/api/dynamic/{entityName}/{id}", entityName, recordId)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void deleteRecordForEntity_WhenEntityNotFound_ShouldReturn404() {
        // Given
        String entityName = "nonexistent";
        when(entityService.getEntityByName(entityName)).thenReturn(Mono.empty());

        // When & Then
        webTestClient.delete()
                .uri("/api/dynamic/{entityName}/{id}", entityName, recordId)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void deleteRecordForEntity_WhenRecordNotFound_ShouldReturn404() {
        // Given
        String entityName = "customer";
        when(entityService.getEntityByName(entityName)).thenReturn(Mono.just(entityDto));
        when(recordService.getRecordById(recordId)).thenReturn(Mono.empty());

        // When & Then
        webTestClient.delete()
                .uri("/api/dynamic/{entityName}/{id}", entityName, recordId)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void queryRecordsForEntity_ShouldReturnQueryResults() {
        // Given
        String entityName = "customer";
        QueryDto queryDto = new QueryDto();
        queryDto.setQueryString("lastName = 'Doe'");
        queryDto.setPage(0);
        queryDto.setSize(10);

        when(entityService.getEntityByName(entityName)).thenReturn(Mono.just(entityDto));
        when(recordService.countQuery(any(QueryDto.class))).thenReturn(Mono.just(1L));
        when(recordService.executeQuery(any(QueryDto.class))).thenReturn(Flux.just(recordDto));

        // When & Then
        webTestClient.post()
                .uri("/api/dynamic/{entityName}/query", entityName)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(queryDto)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.entityName").isEqualTo(entityName)
                .jsonPath("$.totalCount").isEqualTo(1)
                .jsonPath("$.records[0].id").isEqualTo(recordId.toString());
    }

    @Test
    void queryRecordsForEntity_WhenEntityNotFound_ShouldReturn404() {
        // Given
        String entityName = "nonexistent";
        QueryDto queryDto = new QueryDto();
        queryDto.setQueryString("lastName = 'Doe'");

        when(entityService.getEntityByName(entityName)).thenReturn(Mono.empty());

        // When & Then
        webTestClient.post()
                .uri("/api/dynamic/{entityName}/query", entityName)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(queryDto)
                .exchange()
                .expectStatus().isNotFound();
    }
}
