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


package com.firefly.core.modelhub.interfaces.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DTO for representing a complete query with conditions, sorting, and pagination.
 * Supports both structured JSON queries and SQL-like string queries.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Query definition")
public class QueryDto {

    @Schema(description = "Entity ID to query", example = "123e4567-e89b-12d3-a456-426614174000", required = true)
    private UUID entityId;

    @Schema(description = "SQL-like query string (alternative to structured conditions)", 
            example = "firstName = 'John' AND age > 30 ORDER BY lastName ASC")
    private String queryString;

    @Schema(description = "Root condition group for complex nested queries")
    private QueryGroupDto rootGroup;

    @Schema(description = "List of conditions to apply (for backward compatibility)")
    @Builder.Default
    private List<QueryConditionDto> conditions = new ArrayList<>();

    @Schema(description = "Logical operator to combine conditions (for backward compatibility)", 
            example = "AND", defaultValue = "AND")
    @Builder.Default
    private LogicalOperator logicalOperator = LogicalOperator.AND;

    @Schema(description = "Field to sort by", example = "firstName")
    private String sortField;

    @Schema(description = "Sort direction", example = "ASC", defaultValue = "ASC")
    @Builder.Default
    private SortDirection sortDirection = SortDirection.ASC;

    @Schema(description = "Page number (zero-based)", example = "0", defaultValue = "0")
    @Builder.Default
    private int page = 0;

    @Schema(description = "Page size", example = "20", defaultValue = "20")
    @Builder.Default
    private int size = 20;

    /**
     * Enum for logical operators.
     */
    public enum LogicalOperator {
        AND, OR
    }

    /**
     * Enum for sort directions.
     */
    public enum SortDirection {
        ASC, DESC
    }

    /**
     * Add a condition to the query (for backward compatibility).
     *
     * @param field    the field to query on
     * @param operator the operator to use
     * @param value    the value to compare against
     * @return this query for chaining
     */
    public QueryDto addCondition(String field, String operator, Object value) {
        conditions.add(QueryConditionDto.builder()
                .field(field)
                .operator(operator)
                .value(value)
                .build());
        return this;
    }

    /**
     * Create a root condition group with the specified logical operator.
     *
     * @param logicalOperator the logical operator for the root group
     * @return the root group for chaining
     */
    public QueryGroupDto createRootGroup(LogicalOperator logicalOperator) {
        rootGroup = new QueryGroupDto();
        rootGroup.setLogicalOperator(logicalOperator);
        return rootGroup;
    }

    /**
     * Get the root condition group, creating it if it doesn't exist.
     *
     * @return the root group
     */
    public QueryGroupDto getRootGroup() {
        if (rootGroup == null) {
            rootGroup = new QueryGroupDto();
        }
        return rootGroup;
    }
}
