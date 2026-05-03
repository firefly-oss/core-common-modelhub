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


package com.firefly.core.modelhub.core.services;

import com.firefly.core.modelhub.core.mappers.VirtualEntityRecordMapper;
import com.firefly.core.modelhub.core.parsers.SqlQueryParser;
import com.firefly.core.modelhub.interfaces.dtos.QueryDto;
import com.firefly.core.modelhub.interfaces.dtos.VirtualEntityFieldDto;
import com.firefly.core.modelhub.interfaces.dtos.VirtualEntityRecordDto;
import com.firefly.core.modelhub.models.entities.VirtualEntity;
import com.firefly.core.modelhub.models.entities.VirtualEntityRecord;
import com.firefly.core.modelhub.models.repositories.VirtualEntityRecordRepository;
import com.firefly.core.modelhub.models.repositories.VirtualEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import io.r2dbc.postgresql.codec.Json;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class VirtualEntityRecordServiceTest {

    @Mock
    private VirtualEntityRecordRepository recordRepository;

    @Mock
    private VirtualEntityRepository entityRepository;

    @Mock
    private VirtualEntityFieldService fieldService;

    @Mock
    private VirtualEntityRecordMapper recordMapper;

    @Mock
    private SqlQueryParser sqlQueryParser;

    @InjectMocks
    private VirtualEntityRecordService recordService;

    private UUID entityId;
    private UUID recordId;
    private VirtualEntity entity;
    private VirtualEntityRecord record;
    private VirtualEntityRecordDto recordDto;
    private VirtualEntityFieldDto requiredFieldDto;
    private Map<String, Object> payload;

    @BeforeEach
    void setUp() {
        entityId = UUID.randomUUID();
        recordId = UUID.randomUUID();

        entity = VirtualEntity.builder()
                .id(entityId)
                .name("TestEntity")
                .description("Test Entity Description")
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        payload = new HashMap<>();
        payload.put("testField", "Test Value");

        // Create a Json object from the payload map
        Json jsonPayload = Json.of(payload.toString());

        record = VirtualEntityRecord.builder()
                .id(recordId)
                .entityId(entityId)
                .payload(jsonPayload)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        recordDto = VirtualEntityRecordDto.builder()
                .id(recordId)
                .entityId(entityId)
                .payload(payload)
                .build();

        requiredFieldDto = VirtualEntityFieldDto.builder()
                .id(UUID.randomUUID())
                .entityId(entityId)
                .fieldKey("testField")
                .fieldLabel("Test Field")
                .fieldType("string")
                .required(true)
                .orderIndex(1)
                .build();
    }

    @Test
    void getRecordsByEntityId_ShouldReturnRecords() {
        when(recordRepository.findByEntityId(entityId))
                .thenReturn(Flux.just(record));
        when(recordMapper.toDto(record)).thenReturn(recordDto);

        StepVerifier.create(recordService.getRecordsByEntityId(entityId))
                .expectNext(recordDto)
                .verifyComplete();

        verify(recordRepository).findByEntityId(entityId);
        verify(recordMapper).toDto(record);
    }

    @Test
    void getRecordsByEntityIdWithPagination_ShouldReturnRecords() {
        Pageable pageable = PageRequest.of(0, 10);
        when(recordRepository.findByEntityId(entityId, pageable))
                .thenReturn(Flux.just(record));
        when(recordMapper.toDto(record)).thenReturn(recordDto);

        StepVerifier.create(recordService.getRecordsByEntityId(entityId, pageable))
                .expectNext(recordDto)
                .verifyComplete();

        verify(recordRepository).findByEntityId(entityId, pageable);
        verify(recordMapper).toDto(record);
    }

    @Test
    void getRecordById_ShouldReturnRecord() {
        when(recordRepository.findById(recordId)).thenReturn(Mono.just(record));
        when(recordMapper.toDto(record)).thenReturn(recordDto);

        StepVerifier.create(recordService.getRecordById(recordId))
                .expectNext(recordDto)
                .verifyComplete();

        verify(recordRepository).findById(recordId);
        verify(recordMapper).toDto(record);
    }

    @Test
    void getRecordById_WhenNotFound_ShouldReturnEmpty() {
        when(recordRepository.findById(recordId)).thenReturn(Mono.empty());

        StepVerifier.create(recordService.getRecordById(recordId))
                .verifyComplete();

        verify(recordRepository).findById(recordId);
        verify(recordMapper, never()).toDto(any());
    }

    @Test
    void createRecord_ShouldCreateAndReturnRecord() {
        when(entityRepository.findById(entityId)).thenReturn(Mono.just(entity));
        when(fieldService.getFieldsByEntityId(entityId)).thenReturn(Flux.just(requiredFieldDto));
        when(recordMapper.toEntity(recordDto)).thenReturn(record);
        when(recordRepository.save(any(VirtualEntityRecord.class))).thenReturn(Mono.just(record));
        when(recordMapper.toDto(record)).thenReturn(recordDto);

        StepVerifier.create(recordService.createRecord(recordDto))
                .expectNext(recordDto)
                .verifyComplete();

        verify(entityRepository).findById(entityId);
        verify(fieldService).getFieldsByEntityId(entityId);
        verify(recordMapper).toEntity(recordDto);
        verify(recordRepository).save(any(VirtualEntityRecord.class));
        verify(recordMapper).toDto(record);
    }

    @Test
    void createRecord_WhenEntityNotFound_ShouldReturnError() {
        when(entityRepository.findById(entityId)).thenReturn(Mono.empty());

        StepVerifier.create(recordService.createRecord(recordDto))
                .expectErrorMatches(throwable -> 
                    throwable instanceof IllegalArgumentException && 
                    throwable.getMessage().contains("Entity with ID"))
                .verify();

        verify(entityRepository).findById(entityId);
        verify(fieldService, never()).getFieldsByEntityId(any());
        verify(recordMapper, never()).toEntity(any());
        verify(recordRepository, never()).save(any());
    }

    @Test
    void createRecord_WhenMissingRequiredField_ShouldReturnError() {
        Map<String, Object> emptyPayload = new HashMap<>();
        VirtualEntityRecordDto invalidRecordDto = VirtualEntityRecordDto.builder()
                .id(recordId)
                .entityId(entityId)
                .payload(emptyPayload)
                .build();

        when(entityRepository.findById(entityId)).thenReturn(Mono.just(entity));
        when(fieldService.getFieldsByEntityId(entityId)).thenReturn(Flux.just(requiredFieldDto));

        StepVerifier.create(recordService.createRecord(invalidRecordDto))
                .expectErrorMatches(throwable -> 
                    throwable instanceof IllegalArgumentException && 
                    throwable.getMessage().contains("Required field"))
                .verify();

        verify(entityRepository).findById(entityId);
        verify(fieldService).getFieldsByEntityId(entityId);
        verify(recordMapper, never()).toEntity(any());
        verify(recordRepository, never()).save(any());
    }

    @Test
    void updateRecord_ShouldUpdateAndReturnRecord() {
        when(recordRepository.findById(recordId)).thenReturn(Mono.just(record));
        when(fieldService.getFieldsByEntityId(entityId)).thenReturn(Flux.just(requiredFieldDto));
        when(recordMapper.toEntity(recordDto)).thenReturn(record);
        when(recordRepository.save(any(VirtualEntityRecord.class))).thenReturn(Mono.just(record));
        when(recordMapper.toDto(record)).thenReturn(recordDto);

        StepVerifier.create(recordService.updateRecord(recordId, recordDto))
                .expectNext(recordDto)
                .verifyComplete();

        verify(recordRepository).findById(recordId);
        verify(fieldService).getFieldsByEntityId(entityId);
        verify(recordMapper).toEntity(recordDto);
        verify(recordRepository).save(any(VirtualEntityRecord.class));
        verify(recordMapper).toDto(record);
    }

    @Test
    void updateRecord_WhenNotFound_ShouldReturnError() {
        when(recordRepository.findById(recordId)).thenReturn(Mono.empty());

        StepVerifier.create(recordService.updateRecord(recordId, recordDto))
                .expectErrorMatches(throwable -> 
                    throwable instanceof IllegalArgumentException && 
                    throwable.getMessage().contains("Record with ID"))
                .verify();

        verify(recordRepository).findById(recordId);
        verify(fieldService, never()).getFieldsByEntityId(any());
        verify(recordMapper, never()).toEntity(any());
        verify(recordRepository, never()).save(any());
    }

    @Test
    void deleteRecord_ShouldDeleteRecord() {
        when(recordRepository.findById(recordId)).thenReturn(Mono.just(record));
        when(recordRepository.deleteById(recordId)).thenReturn(Mono.empty());

        StepVerifier.create(recordService.deleteRecord(recordId))
                .verifyComplete();

        verify(recordRepository).findById(recordId);
        verify(recordRepository).deleteById(recordId);
    }

    @Test
    void deleteRecord_WhenNotFound_ShouldReturnError() {
        when(recordRepository.findById(recordId)).thenReturn(Mono.empty());

        StepVerifier.create(recordService.deleteRecord(recordId))
                .expectErrorMatches(throwable -> 
                    throwable instanceof IllegalArgumentException && 
                    throwable.getMessage().contains("Record with ID"))
                .verify();

        verify(recordRepository).findById(recordId);
        verify(recordRepository, never()).deleteById(any(UUID.class));
    }

    @Test
    void deleteRecordsByEntityId_ShouldDeleteRecords() {
        when(recordRepository.deleteByEntityId(entityId)).thenReturn(Mono.empty());

        StepVerifier.create(recordService.deleteRecordsByEntityId(entityId))
                .verifyComplete();

        verify(recordRepository).deleteByEntityId(entityId);
    }

    @Test
    void executeQuery_ShouldReturnRecords() {
        QueryDto queryDto = new QueryDto();
        queryDto.setEntityId(entityId);

        when(recordRepository.executeQuery(queryDto)).thenReturn(Flux.just(record));
        when(recordMapper.toDto(record)).thenReturn(recordDto);

        StepVerifier.create(recordService.executeQuery(queryDto))
                .expectNext(recordDto)
                .verifyComplete();

        verify(recordRepository).executeQuery(queryDto);
        verify(recordMapper).toDto(record);
    }

    @Test
    void executeQuery_WithQueryString_ShouldParseAndExecute() {
        QueryDto queryDto = new QueryDto();
        queryDto.setEntityId(entityId);
        queryDto.setQueryString("SELECT * FROM TestEntity WHERE testField = 'Test Value'");

        QueryDto parsedQueryDto = new QueryDto();
        parsedQueryDto.setEntityId(entityId);
        // Assume the parser adds conditions to the query

        when(sqlQueryParser.parse(queryDto)).thenReturn(parsedQueryDto);
        when(recordRepository.executeQuery(parsedQueryDto)).thenReturn(Flux.just(record));
        when(recordMapper.toDto(record)).thenReturn(recordDto);

        StepVerifier.create(recordService.executeQuery(queryDto))
                .expectNext(recordDto)
                .verifyComplete();

        verify(sqlQueryParser).parse(queryDto);
        verify(recordRepository).executeQuery(parsedQueryDto);
        verify(recordMapper).toDto(record);
    }

    @Test
    void countQuery_ShouldReturnCount() {
        QueryDto queryDto = new QueryDto();
        queryDto.setEntityId(entityId);

        when(recordRepository.countQuery(queryDto)).thenReturn(Mono.just(1L));

        StepVerifier.create(recordService.countQuery(queryDto))
                .expectNext(1L)
                .verifyComplete();

        verify(recordRepository).countQuery(queryDto);
    }

    @Test
    void countQuery_WithQueryString_ShouldParseAndCount() {
        QueryDto queryDto = new QueryDto();
        queryDto.setEntityId(entityId);
        queryDto.setQueryString("SELECT COUNT(*) FROM TestEntity WHERE testField = 'Test Value'");

        QueryDto parsedQueryDto = new QueryDto();
        parsedQueryDto.setEntityId(entityId);
        // Assume the parser adds conditions to the query

        when(sqlQueryParser.parse(queryDto)).thenReturn(parsedQueryDto);
        when(recordRepository.countQuery(parsedQueryDto)).thenReturn(Mono.just(1L));

        StepVerifier.create(recordService.countQuery(queryDto))
                .expectNext(1L)
                .verifyComplete();

        verify(sqlQueryParser).parse(queryDto);
        verify(recordRepository).countQuery(parsedQueryDto);
    }
}
