# ModelHub Core Services Test Suite

This directory contains the test suite for the ModelHub Core Services. The tests are designed to verify the functionality of the service layer components that handle business logic for virtual entities, their fields, and records.

## Overview

The test suite uses the following testing frameworks and libraries:
- JUnit 5: For test execution and assertions
- Mockito: For mocking dependencies
- StepVerifier: For testing reactive streams (Project Reactor)

## Test Classes

### VirtualEntityServiceTest

Tests for the `VirtualEntityService` class, which manages virtual entity operations:

- **getAllEntities**: Retrieves all virtual entities
- **getEntityById**: Retrieves a specific entity by ID
- **getEntityByName**: Retrieves a specific entity by name
- **getEntitySchema**: Retrieves the schema (entity + fields) for a specific entity
- **createEntity**: Creates a new virtual entity
- **updateEntity**: Updates an existing virtual entity
- **deleteEntity**: Deletes a virtual entity and its associated fields and records

### VirtualEntityFieldServiceTest

Tests for the `VirtualEntityFieldService` class, which manages virtual entity field operations:

- **getFieldsByEntityId**: Retrieves all fields for a specific entity
- **getFieldById**: Retrieves a specific field by ID
- **createField**: Creates a new field for an entity
- **updateField**: Updates an existing field
- **deleteField**: Deletes a specific field
- **deleteFieldsByEntityId**: Deletes all fields for a specific entity

### VirtualEntityRecordServiceTest

Tests for the `VirtualEntityRecordService` class, which manages virtual entity record operations:

- **getRecordsByEntityId**: Retrieves all records for a specific entity
- **getRecordById**: Retrieves a specific record by ID
- **createRecord**: Creates a new record for an entity
- **updateRecord**: Updates an existing record
- **deleteRecord**: Deletes a specific record
- **deleteRecordsByEntityId**: Deletes all records for a specific entity
- **executeQuery**: Executes a query against entity records
- **countQuery**: Counts records matching a query

### SqlQueryParserTest

Tests for the `SqlQueryParser` class, which parses SQL-like query strings into structured QueryDto objects:

- **parse_EmptyQueryString**: Tests handling of empty query strings
- **parse_NullQueryString**: Tests handling of null query strings
- **parse_SimpleWhereClause**: Tests parsing of simple WHERE clauses
- **parse_MultipleConditionsWithAnd**: Tests parsing of multiple conditions with AND operator
- **parse_MultipleConditionsWithOr**: Tests parsing of multiple conditions with OR operator
- **parse_OrderByClause**: Tests parsing of ORDER BY clauses
- **parse_OrderByWithDirection**: Tests parsing of ORDER BY clauses with direction
- **parse_WhereAndOrderBy**: Tests parsing of combined WHERE and ORDER BY clauses
- **parse_NestedConditions**: Tests parsing of nested conditions with parentheses
- **parse_DifferentOperators**: Tests mapping of different SQL operators
- **parse_NullValue**: Tests handling of NULL values
- **parse_NotNullValue**: Tests handling of NOT NULL values
- **parse_ComplexQuery**: Tests parsing of complex queries with multiple conditions, groups, and sorting

## Testing Approach

Each test follows a similar pattern:
1. **Setup**: Prepare test data and configure mock behavior
2. **Execution**: Call the service method being tested
3. **Verification**: Use StepVerifier to verify the reactive stream results
4. **Mock Verification**: Verify that the expected repository and mapper methods were called

## Error Handling Tests

Each service includes tests for error conditions:
- Entity/field/record not found
- Duplicate entity names or field keys
- Invalid input data

These tests ensure that the services properly handle error conditions and return appropriate error messages.

## SQL Query Parser

The `SqlQueryParser` is used by the `VirtualEntityRecordService` to parse SQL-like query strings provided by users through the API. It converts these strings into structured `QueryDto` objects that can be used to execute queries against the database.

The parser supports:
- WHERE clauses with various comparison operators (=, <>, >, >=, <, <=, LIKE, IN, IS)
- AND/OR logical operators
- ORDER BY clauses with ASC/DESC direction
- Nested conditions with parentheses
- NULL and NOT NULL values

This component is critical for enabling users to query virtual entity records using familiar SQL-like syntax through the API.
