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


package com.firefly.core.modelhub.models.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;
import io.r2dbc.postgresql.codec.Json;

/**
 * Entity representing a field definition for a virtual entity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("virtual_entity_field")
public class VirtualEntityField {

    @Id
    private UUID id;

    @Column("entity_id")
    private UUID entityId;

    @Column("field_key")
    private String fieldKey;

    @Column("field_label")
    private String fieldLabel;

    @Column("field_type")
    private String fieldType;

    @Column("required")
    private Boolean required;

    @Column("options")
    private Json options;

    @Column("order_index")
    private Integer orderIndex;

    @CreatedBy
    @Column("created_by")
    private String createdBy;

    @CreatedDate
    @Column("created_at")
    private LocalDateTime createdAt;

    @LastModifiedBy
    @Column("updated_by")
    private String updatedBy;

    @LastModifiedDate
    @Column("updated_at")
    private LocalDateTime updatedAt;
}