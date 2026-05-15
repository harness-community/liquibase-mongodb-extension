# AGENTS.md - NoSQL Abstract Statements

## Purpose

This package provides abstract base classes and interfaces for NoSQL database statements. It defines the contract that all NoSQL statement implementations (MongoDB, Couchbase, etc.) must follow, enabling:

- Generic NoSQL statement execution through polymorphism
- Consistent statement interface across NoSQL databases
- Shared functionality for common NoSQL operations
- Type-safe statement hierarchy for Liquibase's executor layer

This is the foundation layer that MongoDB-specific statements extend.

## Key Files

- `NoSqlExecuteStatement.java` - Base interface for executable NoSQL statements
- `NoSqlQueryForListStatement.java` - Abstract base for query statements returning lists
- `NoSqlQueryForObjectStatement.java` - Abstract base for query statements returning single objects
- `NoSqlUpdateStatement.java` - Abstract base for update/modification statements
- `NoSqlDatabaseStatement.java` - Abstract base for database-level operations
- `NoSqlCollectionStatement.java` - Abstract base for collection-scoped operations

## Architecture

**Statement Type Hierarchy:**
```
NoSqlExecuteStatement (interface)
├── NoSqlQueryForListStatement (abstract)
│   └── FindAllStatement (MongoDB implementation)
├── NoSqlQueryForObjectStatement (abstract)
│   └── CountDocumentsInCollectionStatement (MongoDB implementation)
├── NoSqlUpdateStatement (abstract)
│   └── InsertOneStatement, UpdateManyStatement (MongoDB implementations)
├── NoSqlDatabaseStatement (abstract)
│   └── DropAllCollectionsStatement (MongoDB implementation)
└── NoSqlCollectionStatement (abstract)
    └── CreateCollectionStatement, CreateIndexStatement (MongoDB implementations)
```

**Key Abstractions:**
- **Execute**: Statements that perform actions without returning data
- **Query**: Statements that return data (lists or single objects)
- **Update**: Statements that modify data
- **Database-scoped**: Operations affecting entire database
- **Collection-scoped**: Operations on specific collections

## Testing

- **Run tests for this package**: `mvn test -Dtest=*NoSqlStatementTest`
- **Test file pattern**: `*NoSqlStatementTest.java` in `src/test/java/liquibase/nosql/statement/`
- **Focus**: Abstract behavior, not database-specific logic

## Dependencies

**Internal:**
- `liquibase.statement.SqlStatement` - Liquibase core statement interface
- `liquibase.nosql.executor.NoSqlExecutor` - Executor that runs these statements

**External:**
- `liquibase-core:4.33.0` - Liquibase framework

## Important Patterns

### Generic Type Parameters
Abstract query statements use generics for type safety:
```java
public abstract class NoSqlQueryForObjectStatement<T> 
    extends AbstractNoSqlStatement {
    
    public abstract T queryForObject(Database database);
}
```

### Template Method Pattern
Abstract base classes define execution flow, concrete classes implement specifics:
```java
// Abstract defines contract
public abstract class NoSqlUpdateStatement {
    public abstract void execute(Database database);
}

// MongoDB provides implementation
public class InsertOneStatement extends NoSqlUpdateStatement {
    @Override
    public void execute(Database database) {
        MongoConnection conn = (MongoConnection) database.getConnection();
        conn.getDatabase().getCollection(collectionName).insertOne(document);
    }
}
```

### Database Agnostic Design
- Abstract classes don't reference MongoDB-specific types
- Concrete implementations handle database-specific APIs
- Enables support for multiple NoSQL databases from same codebase

## DOs

- Extend the appropriate abstract base class for new statement types
- Use generic type parameters for query statements returning data
- Keep abstract classes focused on contract definition, not implementation
- Document the execution contract in abstract class Javadoc
- Provide clear method signatures for database-agnostic operations

## DON'Ts

- Don't put database-specific logic in abstract classes
- Don't reference mongo-java-driver types in this package
- Don't create deep inheritance hierarchies (2-3 levels max)
- Don't mix statement types (query statements shouldn't update data)
- Don't add methods that aren't applicable to all NoSQL databases

## Design Rationale

**Why Abstract Statements?**
1. **Extensibility**: Easy to add support for new NoSQL databases (Couchbase, Cassandra)
2. **Type Safety**: Generic parameters ensure correct return types
3. **Consistency**: All NoSQL databases follow same execution patterns
4. **Testability**: Abstract layer can be tested independently
5. **Liquibase Integration**: Matches Liquibase's SQL statement architecture

**Comparison to SQL Statements:**
- SQL: `liquibase.statement.SqlStatement` interface
- NoSQL: `NoSqlExecuteStatement` interface with typed subclasses
- Both integrate with Liquibase's executor pattern

## Related Packages

- `liquibase.ext.mongodb.statement` - Concrete MongoDB statement implementations
- `liquibase.nosql.executor` - Executors that run these statements
- `liquibase.statement` - Liquibase's SQL statement interfaces (parallel design)
