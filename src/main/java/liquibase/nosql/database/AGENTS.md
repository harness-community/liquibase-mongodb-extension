# AGENTS.md - NoSQL Abstract Database Layer

## Purpose

This package provides abstract base classes for NoSQL database implementations, defining the contract that all NoSQL databases (MongoDB, Couchbase, etc.) must follow. Key responsibilities:

- Define database lifecycle methods (connect, close, validate)
- Specify database capabilities and features
- Abstract connection management
- Provide default implementations for common operations
- Enable consistent behavior across NoSQL databases

This is the foundation layer that MongoDB-specific database classes extend.

## Key Files

- `AbstractNoSqlDatabase.java` - Abstract base class for NoSQL databases
- `AbstractNoSqlConnection.java` - Abstract base class for NoSQL connections
- `NoSqlConnectionPatterns.java` - Connection string pattern definitions (optional)

## Architecture

**Database Abstraction Hierarchy:**
```
liquibase.database.Database (interface)
└── liquibase.database.AbstractJdbcDatabase (SQL databases)

liquibase.database.Database (interface)
└── AbstractNoSqlDatabase (NoSQL base)
    └── MongoLiquibaseDatabase (MongoDB implementation)
        └── MongoConnection (MongoDB-specific connection)
```

**Key Abstractions:**
- **Database**: Top-level database facade
- **Connection**: Connection lifecycle and resource management
- **Capabilities**: Feature detection (transactions, schemas, etc.)
- **Metadata**: Database version, type, default schema

## AbstractNoSqlDatabase

**Purpose:** Base class for all NoSQL databases in Liquibase.

**Key Responsibilities:**
- Implement common database operations
- Define abstract methods for NoSQL-specific behavior
- Manage connection lifecycle
- Report database capabilities
- Handle default values and configuration

**Abstract Methods (must be implemented by subclasses):**
```java
public abstract String getShortName();           // e.g., "mongodb"
public abstract String getDefaultDriver(String url);
public abstract boolean supports(DatabaseProperty property);
```

**Common Implementations (inherited by subclasses):**
```java
public boolean supportsSchemas() { return false; }  // NoSQL typically no schemas
public boolean supportsCatalogs() { return false; } // NoSQL typically no catalogs
public boolean isCorrectDatabaseImplementation(DatabaseConnection conn);
```

## AbstractNoSqlConnection

**Purpose:** Base class for NoSQL database connections.

**Key Responsibilities:**
- Abstract connection string handling
- Connection lifecycle (open, close, isClosed)
- Connection metadata (URL, catalog, attached)
- Resource cleanup

**Abstract Methods (must be implemented by subclasses):**
```java
public abstract void open(String url, Driver driver, Properties info);
public abstract void close();
public abstract boolean isClosed();
```

## Testing

- **Run tests for this package**: `mvn test -Dtest=*NoSqlDatabaseTest`
- **Test file pattern**: `*NoSqlDatabaseTest.java` in `src/test/java/liquibase/nosql/database/`
- **Focus**: Abstract behavior, not database-specific implementation

## Dependencies

**Internal:**
- `liquibase.database.*` - Liquibase core database interfaces
- `liquibase.nosql.executor.NoSqlExecutor` - Executor for NoSQL statements
- `liquibase.nosql.lockservice.AbstractNoSqlLockService` - Lock service base
- `liquibase.nosql.changelog.AbstractNoSqlHistoryService` - History service base

**External:**
- `liquibase-core:4.33.0` - Liquibase framework

## Important Patterns

### Template Method Pattern
Abstract class defines algorithm structure, subclasses fill in specifics:
```java
// AbstractNoSqlDatabase provides template
public void init() throws DatabaseException {
    // Common initialization logic
    validateConnection();
    initializeDefaultSchema();
    // Subclass-specific initialization
    initializeNoSqlSpecifics();
}

// Subclass implements details
protected abstract void initializeNoSqlSpecifics();
```

### Database Capability Discovery
```java
@Override
public boolean supports(DatabaseProperty property) {
    switch (property) {
        case TRANSACTIONS:
            return supportsTransactions();  // Subclass decides
        case SCHEMAS:
            return false;                   // NoSQL common default
        default:
            return super.supports(property);
    }
}
```

### Connection String Abstraction
```java
// Abstract class doesn't know connection format
public abstract class AbstractNoSqlConnection {
    protected String url;
    
    // Subclass parses specific format
    public abstract void open(String url, ...);
}

// MongoDB implementation parses mongodb:// URLs
public class MongoConnection extends AbstractNoSqlConnection {
    public void open(String url, ...) {
        // Parse mongodb:// or mongodb+srv:// format
        MongoClient client = MongoClients.create(url);
    }
}
```

## DOs

- Extend `AbstractNoSqlDatabase` for new NoSQL database support
- Extend `AbstractNoSqlConnection` for connection implementations
- Override capability methods to reflect database features
- Provide meaningful error messages in abstract method stubs
- Implement `equals()` and `hashCode()` based on connection URL
- Use `@Override` annotations for clarity
- Document database-specific quirks in subclass Javadoc

## DON'Ts

- Don't put database-specific logic in abstract classes
- Don't reference specific database APIs (mongo-java-driver) in abstractions
- Don't assume all NoSQL databases have same features
- Don't hardcode values - use configuration or subclass overrides
- Don't create deep inheritance hierarchies (2 levels max)
- Don't mix SQL and NoSQL abstractions (separate hierarchies)

## Design Rationale

**Why Separate NoSQL Abstraction?**

1. **Different Paradigms**:
   - SQL: Tables, rows, columns, schemas, JDBC
   - NoSQL: Collections, documents, no fixed schema, native drivers

2. **Capability Differences**:
   - SQL databases share common capabilities (schemas, catalogs, transactions)
   - NoSQL databases vary widely (some have transactions, others don't)

3. **Connection Management**:
   - SQL: JDBC standard
   - NoSQL: Database-specific drivers (mongo-java-driver, Couchbase SDK, etc.)

4. **Extensibility**:
   - Easy to add new NoSQL databases without affecting SQL implementations
   - Each NoSQL database can define unique capabilities

**Comparison to SQL:**
| Aspect | SQL (AbstractJdbcDatabase) | NoSQL (AbstractNoSqlDatabase) |
|--------|---------------------------|------------------------------|
| Connection | JDBC standard | Database-specific drivers |
| Schemas | Always supported | Typically not supported |
| Transactions | Usually supported | Varies by database |
| Executor | JdbcExecutor | NoSqlExecutor |
| Statement | SqlStatement | NoSqlStatement |

## Future NoSQL Databases

To add support for new NoSQL databases (e.g., Couchbase, Cassandra):

1. Create subclass of `AbstractNoSqlDatabase`: `CouchbaseLiquibaseDatabase`
2. Create subclass of `AbstractNoSqlConnection`: `CouchbaseConnection`
3. Implement abstract methods (getShortName, supports, etc.)
4. Create database-specific statements extending `AbstractNoSqlStatement`
5. Register via SPI: `META-INF/services/liquibase.database.Database`

## Related Packages

- `liquibase.ext.mongodb.database` - Concrete MongoDB database implementation
- `liquibase.nosql.executor` - Executors for NoSQL statements
- `liquibase.nosql.statement` - Abstract NoSQL statement layer
- `liquibase.database` - Liquibase core database interfaces (parallel to SQL)
