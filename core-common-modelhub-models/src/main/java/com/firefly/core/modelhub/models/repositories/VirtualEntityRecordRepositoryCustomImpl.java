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


package com.firefly.core.modelhub.models.repositories;

import com.firefly.core.modelhub.interfaces.dtos.QueryConditionDto;
import com.firefly.core.modelhub.interfaces.dtos.QueryDto;
import com.firefly.core.modelhub.interfaces.dtos.QueryGroupDto;
import com.firefly.core.modelhub.models.entities.VirtualEntityRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of custom repository methods for dynamic queries on VirtualEntityRecord.
 */
@RequiredArgsConstructor
@Slf4j
public class VirtualEntityRecordRepositoryCustomImpl implements VirtualEntityRecordRepositoryCustom {

    private final R2dbcEntityTemplate template;

    @Override
    public Flux<VirtualEntityRecord> executeQuery(QueryDto queryDto) {
        try {
            Query query = buildQuery(queryDto);

            // Apply sorting if specified
            if (queryDto.getSortField() != null && !queryDto.getSortField().isEmpty()) {
                String sortField = "payload->'" + queryDto.getSortField() + "'";
                Sort.Direction direction = queryDto.getSortDirection() == QueryDto.SortDirection.ASC 
                        ? Sort.Direction.ASC 
                        : Sort.Direction.DESC;
                query = query.sort(Sort.by(direction, sortField));
            }

            // Apply pagination
            query = query.limit(queryDto.getSize()).offset((long) queryDto.getPage() * queryDto.getSize());

            return template.select(VirtualEntityRecord.class)
                    .matching(query)
                    .all()
                    .onErrorResume(e -> {
                        log.error("Error executing query: {}", e.getMessage(), e);
                        return Flux.error(new QueryExecutionException("Error executing query: " + e.getMessage(), e));
                    });
        } catch (Exception e) {
            log.error("Error building query: {}", e.getMessage(), e);
            return Flux.error(new QueryBuildException("Error building query: " + e.getMessage(), e));
        }
    }

    @Override
    public Mono<Long> countQuery(QueryDto queryDto) {
        try {
            Query query = buildQuery(queryDto);
            return template.count(query, VirtualEntityRecord.class)
                    .onErrorResume(e -> {
                        log.error("Error counting query results: {}", e.getMessage(), e);
                        return Mono.error(new QueryExecutionException("Error counting query results: " + e.getMessage(), e));
                    });
        } catch (Exception e) {
            log.error("Error building count query: {}", e.getMessage(), e);
            return Mono.error(new QueryBuildException("Error building count query: " + e.getMessage(), e));
        }
    }

    /**
     * Build a query from the QueryDto.
     *
     * @param queryDto the query definition
     * @return the built query
     */
    private Query buildQuery(QueryDto queryDto) {
        List<Criteria> criteriaList = new ArrayList<>();

        // Add entity ID criteria
        criteriaList.add(Criteria.where("entity_id").is(queryDto.getEntityId()));

        // Process conditions based on the query structure
        if (queryDto.getRootGroup() != null && 
            (!queryDto.getRootGroup().getConditions().isEmpty() || !queryDto.getRootGroup().getGroups().isEmpty())) {
            // Use the root group for complex queries
            criteriaList.add(buildGroupCriteria(queryDto.getRootGroup()));
        } else if (!queryDto.getConditions().isEmpty()) {
            // For backward compatibility, use the flat conditions list
            for (QueryConditionDto condition : queryDto.getConditions()) {
                criteriaList.add(buildCriteria(condition));
            }
        }

        // Combine all criteria with AND (entity ID must always match)
        Criteria finalCriteria = criteriaList.stream()
                .reduce(Criteria::and)
                .orElse(Criteria.empty());

        return Query.query(finalCriteria);
    }

    /**
     * Build criteria for a condition group.
     *
     * @param group the condition group
     * @return the built criteria
     */
    private Criteria buildGroupCriteria(QueryGroupDto group) {
        List<Criteria> groupCriteriaList = new ArrayList<>();

        // Process each condition in the group
        for (QueryConditionDto condition : group.getConditions()) {
            groupCriteriaList.add(buildCriteria(condition));
        }

        // Process each nested group
        for (QueryGroupDto nestedGroup : group.getGroups()) {
            groupCriteriaList.add(buildGroupCriteria(nestedGroup));
        }

        // Combine criteria with the specified logical operator
        if (groupCriteriaList.isEmpty()) {
            return Criteria.empty();
        }

        if (group.getLogicalOperator() == QueryDto.LogicalOperator.AND) {
            return groupCriteriaList.stream()
                    .reduce(Criteria::and)
                    .orElse(Criteria.empty());
        } else {
            return groupCriteriaList.stream()
                    .reduce(Criteria::or)
                    .orElse(Criteria.empty());
        }
    }

    /**
     * Build criteria for a single condition.
     *
     * @param condition the query condition
     * @return the built criteria
     */
    private Criteria buildCriteria(QueryConditionDto condition) {
        if (condition.getField() == null || condition.getField().isEmpty()) {
            throw new InvalidQueryConditionException("Field name cannot be empty");
        }

        String fieldPath = "payload->'" + condition.getField() + "'";

        try {
            QueryConditionDto.Operator operator = QueryConditionDto.Operator.fromString(condition.getOperator());
            Object value = condition.getValue();

            // Special handling for NULL values
            if (operator == QueryConditionDto.Operator.IS_NULL || 
                (operator == QueryConditionDto.Operator.EQ && value == null)) {
                return Criteria.where(fieldPath).isNull();
            }

            if (operator == QueryConditionDto.Operator.IS_NOT_NULL || 
                (operator == QueryConditionDto.Operator.NEQ && value == null)) {
                return Criteria.where(fieldPath).isNotNull();
            }

            // Regular operators
            switch (operator) {
                case EQ:
                    return Criteria.where(fieldPath).is(value);
                case NEQ:
                    return Criteria.where(fieldPath).not(value);
                case GT:
                    return Criteria.where(fieldPath).greaterThan(value);
                case GTE:
                    return Criteria.where(fieldPath).greaterThanOrEquals(value);
                case LT:
                    return Criteria.where(fieldPath).lessThan(value);
                case LTE:
                    return Criteria.where(fieldPath).lessThanOrEquals(value);
                case CONTAINS:
                    if (value instanceof String) {
                        // For strings, use PostgreSQL's LIKE operator
                        return Criteria.where(fieldPath + "::text").like("%" + value + "%");
                    } else {
                        // For arrays, use PostgreSQL's @> operator (contains)
                        return Criteria.where(fieldPath).is(value);
                    }
                case STARTS_WITH:
                    return Criteria.where(fieldPath + "::text").like(value + "%");
                case ENDS_WITH:
                    return Criteria.where(fieldPath + "::text").like("%" + value);
                case IN:
                    return Criteria.where(fieldPath).in(value);
                case NOT_IN:
                    return Criteria.where(fieldPath).notIn(value);
                default:
                    throw new InvalidQueryConditionException("Unsupported operator: " + operator);
            }
        } catch (IllegalArgumentException e) {
            throw new InvalidQueryConditionException("Invalid operator: " + condition.getOperator(), e);
        }
    }

    /**
     * Exception thrown when a query cannot be built due to invalid input.
     */
    public static class QueryBuildException extends RuntimeException {
        public QueryBuildException(String message) {
            super(message);
        }

        public QueryBuildException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Exception thrown when a query execution fails.
     */
    public static class QueryExecutionException extends RuntimeException {
        public QueryExecutionException(String message) {
            super(message);
        }

        public QueryExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Exception thrown when a query condition is invalid.
     */
    public static class InvalidQueryConditionException extends RuntimeException {
        public InvalidQueryConditionException(String message) {
            super(message);
        }

        public InvalidQueryConditionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
