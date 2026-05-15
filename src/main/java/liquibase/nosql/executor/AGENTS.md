# AGENTS.md - NoSQL Executors

## Purpose

This package provides the execution engine for NoSQL statements, bridging Liquibase's core executor framework with NoSQL-specific operations. Key responsibilities:

- Execute NoSQL statements against database connections
- Coordinate statement lifecycle (validate, execute, log)
- Handle different statement types (query, update, execute)
- Integrate with Liquibase's change tracking and rollback
- Support specialized executors (Mongosh for shell scripts)

Executors are the runtime layer that actually performs database operations.

## Key Files

- `NoSqlExecutor.java` - Main NoSQL executor for standard MongoDB operations
- `MongoshExecutor.java` - Specialized executor for mongosh shell script execution (Harness enhancement)
- `MongoshGenerator.java` - SQL generator for Mongosh statements (Liquibase integration)
- `AbstractNoSqlExecutor.java` - Base class with common executor functionality

## Architecture

**Execution Flow:**
1. Change generates statements via `generateStatements()`
2. Liquibase selects appropriate executor based on statement type
3. Executor's `execute()` method called with statement and database
4. Executor invokes statement's execution logic
5. Results/exceptions propagated back to Liquibase
6. Change recorded in history service

**Executor Selection:**
```
ExecutorService.getExecutor(database)
├── NoSqlExecutor (standard operations)
│   └── Handles: CreateCollectionStatement, InsertOneStatement, etc.
└── MongoshExecutor (shell scripts)
    └── Handles: MongoshStatement
```

**Class Hierarchy:**
```
liquibase.executor.Executor (interface)
└── AbstractNoSqlExecutor (NoSQL base)
    ├── NoSqlExecutor (standard MongoDB operations)
    └── MongoshExecutor (mongosh shell execution)
```

## NoSqlExecutor

**Purpose:** Executes standard MongoDB operations using mongo-java-driver.

**Supported Statement Types:**
- Query statements: `queryForList()`, `queryForObject()`, `queryForLong()`
- Update statements: `execute()` for inserts, updates, deletes
- Database statements: Collection and index management

**Key Methods:**
```java
// Execute update/modification statement
void execute(SqlStatement statement, Database database);

// Query returning list of results
<T> List<T> queryForList(SqlStatement statement, Class<T> type);

// Query returning single object
<T> T queryForObject(SqlStatement statement, Class<T> type);

// Query returning long/count
long queryForLong(SqlStatement statement);
```

## MongoshExecutor (Harness Enhancement)

**Purpose:** Executes JavaScript/MongoDB shell scripts via `mongosh` command-line tool.

**Use Cases:**
- Complex operations not supported by standard changes
- Migration scripts from legacy MongoDB shell
- Bulk operations requiring JavaScript logic
- Admin commands requiring shell syntax

**Execution Mechanism:**
1. Receives `MongoshStatement` with JavaScript code
2. Writes script to temporary file
3. Invokes `mongosh` process with connection string and script
4. Captures stdout/stderr output
5. Returns exit code and output to Liquibase

**Key Features:**
- Automatic connection string injection
- Script validation before execution
- Output capture for logging
- Error handling with exit codes
- Temporary file cleanup

## MongoshGenerator

**Purpose:** SQL generator bridge for Mongosh statements (Liquibase compatibility).

Liquibase expects SQL generators for statements. This class provides the integration layer:
- Registers as `SqlGenerator` for `MongoshStatement`
- Delegates actual execution to `MongoshExecutor`
- Handles validation and priority

## Testing

- **Run tests for this package**: `mvn test -Dtest=*ExecutorTest`
- **Test file pattern**: `*ExecutorTest.java` in `src/test/java/liquibase/nosql/executor/`
- **Integration tests**: Executor tests require MongoDB connection
- **Mongosh tests**: Also require `mongosh` command available in PATH

## Dependencies

**Internal:**
- `liquibase.nosql.statement.*` - Statement interfaces executed by executors
- `liquibase.ext.mongodb.statement.*` - Concrete MongoDB statements
- `liquibase.ext.mongodb.database.MongoConnection` - Database connection

**External:**
- `liquibase.executor.*` - Liquibase core executor interfaces
- `org.mongodb:mongodb-driver-sync` - For NoSqlExecutor operations
- `mongosh` command-line tool - For MongoshExecutor (must be installed separately)

## Important Patterns

### Statement Type Dispatch
```java
@Override
public void execute(SqlStatement statement, Database database) {
    if (statement instanceof NoSqlUpdateStatement) {
        ((NoSqlUpdateStatement) statement).execute(database);
    } else if (statement instanceof NoSqlExecuteStatement) {
        ((NoSqlExecuteStatement) statement).execute(database);
    }
    // ... other statement types
}
```

### Generic Query Execution
```java
@Override
public <T> List<T> queryForList(SqlStatement statement, Class<T> type) {
    if (statement instanceof NoSqlQueryForListStatement) {
        return ((NoSqlQueryForListStatement<T>) statement).queryForList(database);
    }
    throw new DatabaseException("Unsupported query statement type");
}
```

### Mongosh Process Invocation
```java
ProcessBuilder pb = new ProcessBuilder(
    "mongosh",
    connectionString,
    "--quiet",
    "--file", scriptFile.getAbsolutePath()
);
Process process = pb.start();
int exitCode = process.waitFor();
```

## DOs

- Use `NoSqlExecutor` for all standard MongoDB operations
- Use `MongoshExecutor` only when operations cannot be expressed as standard changes
- Validate statements before execution
- Log statement execution for debugging
- Handle exceptions at statement level, not executor level
- Ensure `mongosh` is installed and in PATH for Mongosh operations
- Test executors with both successful and failing statements

## DON'Ts

- Don't create custom executors unless absolutely necessary
- Don't put business logic in executors - belongs in statements
- Don't catch and swallow exceptions - propagate to Liquibase
- Don't execute statements directly - use executor methods
- Don't use `MongoshExecutor` for simple operations (performance overhead)
- Don't assume `mongosh` is always available (check before using)

## Mongosh Requirements

**Installation:**
```bash
# macOS
brew install mongosh

# Linux
wget https://downloads.mongodb.com/compass/mongosh-x.x.x-linux-x64.tgz
tar -xvzf mongosh-x.x.x-linux-x64.tgz
sudo mv mongosh /usr/local/bin/

# Windows
# Download and install from MongoDB website
```

**Verification:**
```bash
mongosh --version
```

**Configuration:**
- Ensure `mongosh` is in system PATH
- Connection string must be accessible from command line
- Test connectivity: `mongosh "mongodb://localhost:27017/test"`

## Performance Considerations

**NoSqlExecutor:**
- Fast: Direct mongo-java-driver calls
- Connection pooling handled by MongoClient
- Suitable for high-frequency operations

**MongoshExecutor:**
- Slower: Process spawn overhead (~100-500ms per execution)
- No connection pooling (new connection per script)
- Suitable for complex, infrequent operations
- Consider batching multiple operations in single script

## Error Handling

**NoSqlExecutor:**
- MongoDB exceptions propagated as `DatabaseException`
- Connection failures handled by mongo-java-driver retry logic
- Statement validation errors before execution

**MongoshExecutor:**
- Non-zero exit codes throw `DatabaseException`
- Stdout captured for debugging
- Stderr captured for error messages
- Timeout protection (default: 60 seconds)

## Related Packages

- `liquibase.ext.mongodb.statement` - MongoDB statements executed by these executors
- `liquibase.nosql.statement` - Abstract statement interfaces
- `liquibase.ext.mongodb.change` - Changes that generate statements
- `liquibase.ext.mongodb.database` - Database connection used by executors
