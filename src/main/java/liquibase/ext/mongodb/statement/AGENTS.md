# AGENTS.md - MongoDB Statements

## Purpose

This package contains MongoDB statement implementations that represent executable database operations. Statements are the execution layer that bridges Liquibase changes to actual MongoDB operations via the mongo-java-driver.

Each statement class encapsulates:
- MongoDB operation parameters (collection names, documents, options)
- Command structure for execution
- Validation logic
- BSON document handling

## Key Files

- `AbstractMongoStatement.java` - Base class for all MongoDB statements with common functionality
- `AbstractCollectionStatement.java` - Base for statements that operate on a specific collection
- `AbstractRunCommandStatement.java` - Base for runCommand/adminCommand operations
- `BsonUtils.java` - Utility class for BSON document parsing and manipulation

### Collection Operations
- `CreateCollectionStatement.java` - Creates collections with validators and options
- `DropCollectionStatement.java` - Removes collections
- `DropAllCollectionsStatement.java` - Drops all collections in database
- `ListCollectionNamesStatement.java` - Lists collection names
- `AuthorizedListCollectionsStatement.java` - Lists full collection info (including `options`) restricted to authorized collections, draining every `getMore` batch; used by the generate-changelog snapshot generators
- `CountCollectionByNameStatement.java` - Checks collection existence by counting

### Index Operations
- `CreateIndexStatement.java` - Creates indexes with options
- `DropIndexStatement.java` - Removes indexes by keys

### Document Operations
- `InsertOneStatement.java` - Inserts single document
- `InsertManyStatement.java` - Inserts multiple documents
- `UpdateManyStatement.java` - Updates documents matching filter
- `DeleteManyStatement.java` - Deletes documents matching filter
- `FindOneAndUpdateStatement.java` - Atomic find-and-update operation
- `FindAllStatement.java` - Query documents with optional filter
- `CountDocumentsInCollectionStatement.java` - Counts documents matching filter

### Command Operations
- `RunCommandStatement.java` - Executes db.runCommand() operations
- `AdminCommandStatement.java` - Executes db.adminCommand() operations
- `MongoshStatement.java` - Executes mongosh shell scripts (Harness enhancement)

## Architecture

**Execution Flow:**
1. Change classes (in `liquibase.ext.mongodb.change`) generate statements
2. Statements are passed to `NoSqlExecutor` or `MongoshExecutor`
3. Executor invokes `execute()` method on statement with MongoDB connection
4. Statement uses mongo-java-driver API to perform operation

**Class Hierarchy:**
```
AbstractNoSqlStatement
└── AbstractMongoStatement
    ├── AbstractCollectionStatement (collection-scoped, direct driver calls)
    │   ├── FindAllStatement
    │   ├── UpdateManyStatement
    │   ├── DeleteManyStatement
    │   ├── CountDocumentsInCollectionStatement
    │   ├── GetMaxChangeSetSequenceStatement
    │   ├── ReplaceChangeLogLockStatement
    │   └── SelectChangeLogLockStatement
    ├── AbstractRunCommandStatement (built as a runCommand document)
    │   ├── RunCommandStatement
    │   │   └── AdminCommandStatement
    │   ├── ListCollectionNamesStatement
    │   │   └── CountCollectionByNameStatement
    │   ├── AuthorizedListCollectionsStatement
    │   ├── CreateCollectionStatement
    │   ├── CreateIndexStatement
    │   ├── DropCollectionStatement
    │   ├── DropIndexStatement
    │   ├── InsertManyStatement
    │   │   └── InsertOneStatement
    │   └── FindOneAndUpdateStatement
    └── DropAllCollectionsStatement (database-scoped)

MongoshStatement sits outside this hierarchy (extends AbstractSqlStatement, run by MongoshExecutor).
```

## Testing

- **Run tests for this package**: `mvn test -Dtest=*StatementTest`
- **Test file pattern**: `*StatementTest.java` in `src/test/java/liquibase/ext/mongodb/statement/`
- **Integration tests**: Some statements have integration tests requiring MongoDB connection

## Dependencies

**Internal:**
- `liquibase.ext.mongodb.database.MongoConnection` - MongoDB connection wrapper
- `liquibase.nosql.statement.*` - Abstract NoSQL statement interfaces
- `liquibase.ext.mongodb.configuration.MongoConfiguration` - Configuration constants

**External:**
- `org.mongodb:mongodb-driver-sync` (5.5.1) - MongoDB Java Driver for all operations
- `org.bson.*` - BSON document handling

## Important Patterns

### Statement Creation
Statements use builder pattern or direct constructor with named parameters:
```java
CreateIndexStatement statement = new CreateIndexStatement(
    collectionName, 
    keys,           // BSON document defining index keys
    options         // IndexOptions
);
```

### BSON Document Handling
- Use `BsonUtils` for parsing JSON strings to BSON documents
- All document parameters are `Document` or `Bson` types from mongo-java-driver
- JSON strings from XML changesets are converted to BSON in statement constructors

### Error Handling
- Statements throw `DatabaseException` for validation errors
- MongoDB driver exceptions are propagated to executor layer
- Null safety checks in constructors and execute methods

## DOs

- Extend `AbstractMongoStatement` or `AbstractCollectionStatement` for new statement types
- Use `BsonUtils.orEmptyDocument()` for optional BSON parameters
- Validate required parameters in constructor
- Keep statements immutable (final fields, no setters)
- Use mongo-java-driver's `Document` and `Bson` types for all MongoDB data
- Add `@Override toString()` for debugging and logging

## DON'Ts

- Don't put business logic in statements - they should only execute operations
- Don't catch MongoDB driver exceptions - let them propagate, except in a best-effort cleanup path in a `finally` block, where swallowing and logging is correct so the original failure isn't masked
- Don't reach past a Change to its statement in a change-level test - go through the Change class. A statement's own `*StatementTest` constructs it directly, and a statement with no Change behind it (`AuthorizedListCollectionsStatement`, driven by the snapshot generators) can only be tested that way
- Don't use deprecated `db.eval()` style operations
- Don't hardcode database names - use connection's database

## Related Packages

- `liquibase.ext.mongodb.change` - Change classes that generate these statements
- `liquibase.nosql.executor` - Executors that run these statements
- `liquibase.ext.mongodb.database` - MongoConnection used by statements
