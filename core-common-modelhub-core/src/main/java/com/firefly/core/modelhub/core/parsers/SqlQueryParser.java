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


package com.firefly.core.modelhub.core.parsers;

import com.firefly.core.modelhub.interfaces.dtos.QueryDto;
import com.firefly.core.modelhub.interfaces.dtos.QueryGroupDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for SQL-like query strings.
 * Converts a SQL-like query string into a structured QueryDto object.
 */
@Component
@Slf4j
public class SqlQueryParser {

    // Regex patterns for parsing
    private static final Pattern WHERE_PATTERN = Pattern.compile("(?i)\\s*WHERE\\s+(.+?)(?:\\s+ORDER\\s+BY\\s+|$)");
    private static final Pattern ORDER_BY_PATTERN = Pattern.compile("(?i)\\s*ORDER\\s+BY\\s+([^\\s]+)(?:\\s+(ASC|DESC))?");
    private static final Pattern CONDITION_PATTERN = Pattern.compile("([^\\s]+)\\s*([=<>!]+|LIKE|IN|IS)\\s*([^\\s]+|'[^']*'|\\([^)]*\\))");
    private static final Pattern LOGICAL_OP_PATTERN = Pattern.compile("(?i)\\s+(AND|OR)\\s+");
    private static final Pattern PARENTHESIS_PATTERN = Pattern.compile("\\(([^()]+)\\)");

    /**
     * Parse a SQL-like query string into a QueryDto object.
     *
     * @param queryDto the QueryDto containing the query string and other parameters
     * @return the updated QueryDto with structured query information
     */
    public QueryDto parse(QueryDto queryDto) {
        if (queryDto.getQueryString() == null || queryDto.getQueryString().trim().isEmpty()) {
            return queryDto; // Nothing to parse
        }

        String queryString = queryDto.getQueryString().trim();
        
        // Parse ORDER BY clause
        Matcher orderByMatcher = ORDER_BY_PATTERN.matcher(queryString);
        if (orderByMatcher.find()) {
            String sortField = orderByMatcher.group(1);
            queryDto.setSortField(sortField);
            
            if (orderByMatcher.groupCount() > 1 && orderByMatcher.group(2) != null) {
                String direction = orderByMatcher.group(2);
                queryDto.setSortDirection("DESC".equalsIgnoreCase(direction) 
                        ? QueryDto.SortDirection.DESC 
                        : QueryDto.SortDirection.ASC);
            }
        }
        
        // Parse WHERE clause
        Matcher whereMatcher = WHERE_PATTERN.matcher(queryString);
        if (whereMatcher.find()) {
            String whereClause = whereMatcher.group(1);
            parseWhereClause(whereClause, queryDto);
        }
        
        return queryDto;
    }
    
    /**
     * Parse the WHERE clause of a SQL-like query.
     *
     * @param whereClause the WHERE clause to parse
     * @param queryDto the QueryDto to update
     */
    private void parseWhereClause(String whereClause, QueryDto queryDto) {
        // Handle parentheses for nested conditions
        while (true) {
            Matcher parenthesisMatcher = PARENTHESIS_PATTERN.matcher(whereClause);
            if (!parenthesisMatcher.find()) {
                break;
            }
            
            // Replace the parenthesized expression with a placeholder
            String innerExpression = parenthesisMatcher.group(1);
            whereClause = whereClause.replace("(" + innerExpression + ")", "PLACEHOLDER");
            
            // Parse the inner expression and add it as a nested group
            QueryGroupDto group = new QueryGroupDto();
            parseConditions(innerExpression, group);
            
            // Add the group to the root group
            if (queryDto.getRootGroup().getConditions().isEmpty() && 
                queryDto.getRootGroup().getGroups().isEmpty()) {
                // This is the first condition, just set the root group
                queryDto.setRootGroup(group);
            } else {
                // Add as a nested group
                queryDto.getRootGroup().getGroups().add(group);
            }
        }
        
        // Parse the remaining conditions
        parseConditions(whereClause, queryDto.getRootGroup());
    }
    
    /**
     * Parse conditions from a string and add them to a group.
     *
     * @param conditionsString the string containing conditions
     * @param group the group to add conditions to
     */
    private void parseConditions(String conditionsString, QueryGroupDto group) {
        // Split by AND/OR
        String[] parts = LOGICAL_OP_PATTERN.split(conditionsString);
        List<String> operators = new ArrayList<>();
        
        Matcher opMatcher = LOGICAL_OP_PATTERN.matcher(conditionsString);
        while (opMatcher.find()) {
            operators.add(opMatcher.group(1));
        }
        
        // Determine the logical operator for the group
        if (!operators.isEmpty() && operators.stream().allMatch(op -> op.equalsIgnoreCase("OR"))) {
            group.setLogicalOperator(QueryDto.LogicalOperator.OR);
        } else {
            group.setLogicalOperator(QueryDto.LogicalOperator.AND);
        }
        
        // Parse each condition
        for (String part : parts) {
            if (part.trim().equalsIgnoreCase("PLACEHOLDER")) {
                // This is a placeholder for a nested group, already handled
                continue;
            }
            
            Matcher conditionMatcher = CONDITION_PATTERN.matcher(part);
            if (conditionMatcher.find()) {
                String field = conditionMatcher.group(1);
                String operator = mapOperator(conditionMatcher.group(2));
                String value = parseValue(conditionMatcher.group(3));
                
                group.addCondition(field, operator, value);
            }
        }
    }
    
    /**
     * Map SQL operators to our operator enum values.
     *
     * @param sqlOperator the SQL operator
     * @return the corresponding operator enum value
     */
    private String mapOperator(String sqlOperator) {
        switch (sqlOperator.toUpperCase()) {
            case "=": return "eq";
            case "!=": 
            case "<>": return "neq";
            case ">": return "gt";
            case ">=": return "gte";
            case "<": return "lt";
            case "<=": return "lte";
            case "LIKE": return "contains";
            case "IN": return "in";
            case "IS": return "isNull"; // Will be adjusted based on the value
            default: return "eq";
        }
    }
    
    /**
     * Parse a value from a string.
     *
     * @param valueStr the string representation of the value
     * @return the parsed value
     */
    private String parseValue(String valueStr) {
        // Remove quotes for string literals
        if (valueStr.startsWith("'") && valueStr.endsWith("'")) {
            return valueStr.substring(1, valueStr.length() - 1);
        }
        
        // Handle NULL values
        if (valueStr.equalsIgnoreCase("NULL")) {
            return null;
        }
        
        // Handle NOT NULL
        if (valueStr.equalsIgnoreCase("NOT NULL")) {
            return "NOT NULL";
        }
        
        return valueStr;
    }
}