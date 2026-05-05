# AGENTS.md - MongoDB Change Types

## Purpose

This package contains **Liquibase change type implementations** for MongoDB operations. Change types are the core building blocks that users specify in their changelog files (XML, YAML, JSON) to define database schema migrations.

Each change type corresponds to a MongoDB operation (createIndex, insertOne, etc.) and extends either `AbstractMongoChange` or `AbstractSQLChange` from Liquibase's framework.

## Key Files

### Base Classes
- **`AbstractMongoChange.java`** - Base class for all MongoDB change types, extends Liquibase's `AbstractChange`
  - Handles MongoDB-specific validation and execution logic
  - Provides common checksum and database type checking

### Standard MongoDB Operations
- **`CreateIndexChange.java`** - Create indexes on MongoDB collections
- **`DropIndexChange.java`** - Drop indexes from MongoDB collections
- **`CreateCollectionChange.java`** - Create new MongoDB collections with options
- **`DropCollectionChange.java`** - Drop MongoDB collections
- **`InsertOneChange.java`** - Insert a single document into a collection
- **`InsertManyChange.java`** - Insert multiple documents into a collection
- **`RunCommandChange.java`** - Execute arbitrary MongoDB database commands
- **`AdminCommandChange.java`** - Execute MongoDB admin commands

### Harness Enhancements (Mongosh Native Executor)
- **`MongoshChange.java`** - Execute inline JavaScript/MongoDB shell commands via mongosh (changeType: `mongo`)
  - Most actively modified file in this package
  - Allows executing native MongoDB shell commands directly in changelog
  - Uses selective property serialization to keep YAML clean
- **`MongoshFileChange.java`** - Execute MongoDB shell commands from external .js files (changeType: `mongoFile`)
  - Second most actively modified file
  - Supports loading and executing mongosh scripts from file paths

## Testing

- **Run tests for this package**: `mvn test -Dtest=*ChangeTest`
- **Test file pattern**: `*Change.java` → `*ChangeTest.java` in `src/test/java/liquibase/ext/mongodb/change/`
- **Integration tests**: Some changes have IT tests: `mvn verify -Dit.test=*IT`

## Dependencies

**Internal:**
- `liquibase.ext.mongodb.database.*` - MongoDB database connection and driver
- `liquibase.ext.mongodb.statement.*` - Statement classes for executing operations
- `liquibase.nosql.executor.MongoshExecutor` - Executor for mongosh commands

**External:**
- Liquibase Core - `liquibase.change.*`, `liquibase.database.*`
- MongoDB Java Driver - For MongoDB-specific operations
- Lombok - `@Getter`, `@Setter`, `@NoArgsConstructor` annotations

## Important Patterns

### Change Type Registration
All change types use `@DatabaseChange` annotation to register with Liquibase:
```java
@DatabaseChange(
    name = "createIndex",
    description = "Creates an index on a collection",
    priority = 1
)
```

### Selective Property Serialization (Mongosh Changes)
`MongoshChange` and `MongoshFileChange` implement selective serialization to exclude unnecessary properties from changelog YAML files:
- Only `mongo` property is serialized for MongoshChange
- Only `mongoFile` property is serialized for MongoshFileChange
- This prevents pollution with inherited Liquibase properties like `sql`, `dbms`, `endDelimiter`

### Statement Generation
Each change type generates corresponding statement(s):
```java
@Override
public SqlStatement[] generateStatements(Database database) {
    return new SqlStatement[] { new MongoshStatement(getMongo()) };
}
```

### Validation
Implement `validate()` method to validate change parameters before execution

## DOs

- Extend `AbstractMongoChange` for new MongoDB-specific change types
- Use `@DatabaseChange` annotation with unique name and description
- Add corresponding statement class in `statement/` package
- Implement selective serialization if needed (override `getSerializableFields()`)
- Add unit tests in `src/test/java/.../change/` with naming pattern `*ChangeTest.java`
- Add integration tests for database operations: `*IT.java`
- Use Lombok annotations (`@Getter`, `@Setter`, `@NoArgsConstructor`) for boilerplate
- Update XSD schema files in `src/main/resources/www.liquibase.org/xml/ns/mongodb/` when adding new attributes
- Register new change types in `META-INF/services/liquibase.change.Change`

## DON'Ts

- Don't modify the base `AbstractMongoChange` without thorough testing across all subclasses
- Don't skip validation logic - always validate user inputs
- Don't hardcode database-specific logic - use statements for execution
- Don't add properties without considering YAML serialization (use selective serialization)
- Don't break backward compatibility with existing changesets
- Don't change `@DatabaseChange.name` - this breaks existing changelogs
- Don't skip checksum calculation logic (affects Liquibase's change detection)

## Recent Development Focus

The most recent development has focused on:
1. **Mongosh native executor** - `MongoshChange` and `MongoshFileChange` (13 commits in 6 months)
2. **Selective property serialization** - Keeping YAML changelogs clean
3. **MongoIndexExists precondition** - Related precondition support

These features enable users to execute arbitrary MongoDB shell commands while maintaining clean, readable changelog files.
