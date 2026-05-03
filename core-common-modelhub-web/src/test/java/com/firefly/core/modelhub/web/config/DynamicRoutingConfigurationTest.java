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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
public class DynamicRoutingConfigurationTest {

    @Mock
    private VirtualEntityService entityService;

    @Mock
    private VirtualEntityRecordService recordService;

    @Mock
    private VirtualEntityFieldService fieldService;

    @Mock
    private RecordValidator recordValidator;

    private DynamicRoutingConfiguration routingConfiguration;
    private WebTestClient webTestClient;
    private UUID entityId;
    private UUID recordId;
    private VirtualEntityDto entityDto;
    private VirtualEntityRecordDto recordDto;
    private Map<String, Object> recordPayload;

    @BeforeEach
    void setUp() {
        routingConfiguration = new DynamicRoutingConfiguration(entityService, recordService, fieldService, recordValidator);
        RouterFunction<ServerResponse> routerFunction = routingConfiguration.dynamicEntityRoutes();
        webTestClient = WebTestClient.bindToRouterFunction(routerFunction).build();

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
    void getAllRecords_ShouldReturnRecords() {
        // Given
        String entityName = "customer";
        when(entityService.getEntityByName(entityName)).thenReturn(Mono.just(entityDto));
        when(recordService.getRecordsByEntityId(eq(entityId), any(PageRequest.class)))
                .thenReturn(Flux.just(recordDto));

        // When & Then
        webTestClient.get()
                .uri("/api/{entityName}?page=0&size=20", entityName)
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
    void getAllRecords_WhenEntityNotFound_ShouldReturn404() {
        // Given
        String entityName = "nonexistent";
        when(entityService.getEntityByName(entityName)).thenReturn(Mono.empty());

        // When & Then
        webTestClient.get()
                .uri("/api/{entityName}", entityName)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void getRecordById_ShouldReturnRecord() {
        // Given
        String entityName = "customer";
        when(entityService.getEntityByName(entityName)).thenReturn(Mono.just(entityDto));
        when(recordService.getRecordById(recordId)).thenReturn(Mono.just(recordDto));

        // When & Then
        webTestClient.get()
                .uri("/api/{entityName}/{id}", entityName, recordId)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(recordId.toString())
                .jsonPath("$.entityId").isEqualTo(entityId.toString())
                .jsonPath("$.payload.firstName").isEqualTo("John");
    }

    @Test
    void getRecordById_WithInvalidUUID_ShouldReturn400() {
        // Given
        String entityName = "customer";
        String invalidId = "not-a-uuid";

        // When & Then
        webTestClient.get()
                .uri("/api/{entityName}/{id}", entityName, invalidId)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error").isEqualTo("Invalid Long format");
    }

    @Test
    void createRecord_ShouldCreateAndReturnRecord() {
        // Given
        String entityName = "customer";
        when(entityService.getEntityByName(entityName)).thenReturn(Mono.just(entityDto));
        when(recordService.createRecord(any(VirtualEntityRecordDto.class))).thenReturn(Mono.just(recordDto));
        doReturn(Mono.empty()).when(recordValidator).validateRecord(any(), any());

        // When & Then
        webTestClient.post()
                .uri("/api/{entityName}", entityName)
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
    void updateRecord_ShouldUpdateAndReturnRecord() {
        // Given
        String entityName = "customer";
        when(entityService.getEntityByName(entityName)).thenReturn(Mono.just(entityDto));
        when(recordService.getRecordById(recordId)).thenReturn(Mono.just(recordDto));
        when(recordService.updateRecord(eq(recordId), any(VirtualEntityRecordDto.class))).thenReturn(Mono.just(recordDto));

        // When & Then
        webTestClient.put()
                .uri("/api/{entityName}/{id}", entityName, recordId)
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
    void deleteRecord_ShouldDeleteRecord() {
        // Given
        String entityName = "customer";
        when(entityService.getEntityByName(entityName)).thenReturn(Mono.just(entityDto));
        when(recordService.getRecordById(recordId)).thenReturn(Mono.just(recordDto));
        when(recordService.deleteRecord(recordId)).thenReturn(Mono.empty());

        // When & Then
        webTestClient.delete()
                .uri("/api/{entityName}/{id}", entityName, recordId)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void queryRecords_ShouldReturnQueryResults() {
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
                .uri("/api/{entityName}/query", entityName)
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
    void routesShouldNotHandleReservedPaths() {
        // Given
        String reservedPath1 = "v1";
        String reservedPath2 = "dynamic";

        // No need to mock hasElement() since we're checking reserved paths synchronously

        // When & Then - These should not be handled by our router
        webTestClient.get()
                .uri("/api/{entityName}", reservedPath1)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound();

        webTestClient.get()
                .uri("/api/{entityName}", reservedPath2)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound();
    }
}
