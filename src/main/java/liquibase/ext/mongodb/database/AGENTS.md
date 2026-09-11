# AGENTS.md - MongoDB Database Connection

## Purpose

This package implements the database connection layer for MongoDB, providing the integration point between Liquibase and MongoDB. Key responsibilities:

- MongoDB connection management and lifecycle
- Database metadata and capabilities
- Connection string parsing and validation
- Database-specific features and limitations
- Transaction support (where applicable)

This is the entry point that Liquibase uses to interact with MongoDB.

## Key Files

- `MongoLiquibaseDatabase.java` - Main database facade implementing Liquibase's Database interface. Overrides `supports(Class)` so snapshot/diff only include mongo Collection, mongo Index, and Catalog (needed for core `generate-changelog`).
- `MongoConnection.java` - MongoDB connection wrapper managing MongoClient and database instances
- `MongoClientDriver.java` - JDBC-style driver for MongoDB (Liquibase compatibility layer)
- `MongoConnectionStringParser.java` - Parses MongoDB connection strings (standard and DNS seed list formats)
- `MongoVisibleUrlBuilder.java` - Builds sanitized connection URLs for logging (removes credentials)

## Architecture

**Connection Lifecycle:**
1. Liquibase creates `MongoLiquibaseDatabase` instance
2. `setConnection(url, username, password)` called with connection details
3. `MongoConnection` creates `MongoClient` using connection string
4. `MongoClient` connects to MongoDB cluster
5. Operations executed via `getConnection().getDatabase()`
6. `close()` releases MongoClient resources

**Class Hierarchy:**
```
liquibase.database.Database (interface)
└── liquibase.nosql.database.AbstractNoSqlDatabase
    └── MongoLiquibaseDatabase
        └── MongoConnection (composition)
            └── MongoClient (mongo-java-driver)
```

## Connection String Formats

### Standard Format
```
mongodb://[username:password@]host1[:port1][,...hostN[:portN]][/[defaultauthdb][?options]]
```

Examples:
- `mongodb://localhost:27017/mydb`
- `mongodb://user:pass@host1:27017,host2:27017/mydb?replicaSet=rs0`
- `mongodb://localhost:27017/mydb?authSource=admin`

### DNS Seed List Format
```
mongodb+srv://[username:password@]host[/[database][?options]]
```

Examples:
- `mongodb+srv://server.example.com/mydb`
- `mongodb+srv://user:pass@cluster0.mongodb.net/mydb?retryWrites=true`

## Testing

- **Run tests for this package**: `mvn test -Dtest=*DatabaseTest`
- **Test file pattern**: `*DatabaseTest.java` in `src/test/java/liquibase/ext/mongodb/database/`
- **Integration tests**: Connection tests require running MongoDB instance

## Dependencies

**Internal:**
- `liquibase.nosql.database.AbstractNoSqlDatabase` - Base NoSQL database class
- `liquibase.ext.mongodb.configuration.MongoConfiguration` - Configuration constants
- `liquibase.ext.mongodb.lockservice.MongoLockService` - Lock service integration
- `liquibase.ext.mongodb.changelog.MongoHistoryService` - History service integration

**External:**
- `org.mongodb:mongodb-driver-sync:5.5.1` - MongoDB Java Driver
- `liquibase.database.*` - Liquibase core database interfaces

## Important Patterns

### Database Identification
```java
@Override
public String getShortName() {
    return "mongodb";
}

@Override
public String getDefaultDriver(String url) {
    if (url.startsWith("mongodb://") || url.startsWith("mongodb+srv://")) {
        return MongoClientDriver.class.getName();
    }
    return null;
}
```

### Connection URL Sanitization
```java
// getVisibleUrl() removes credentials for safe logging
// Input:  mongodb://user:password@host:27017/db
// Output: mongodb://***:***@host:27017/db
```

### Database Capabilities
```java
@Override
public boolean supports(DatabaseProperty property) {
    switch (property) {
        case CATALOGS: return false;          // MongoDB has no catalogs
        case SCHEMAS: return false;           // MongoDB has no schemas
        case TRANSACTIONS: return true;       // MongoDB 4.0+ supports transactions
        case SEQUENCES: return false;         // MongoDB has no sequences
        // ... other capabilities
    }
}
```

### Connection Pooling
- `MongoClient` internally manages connection pool
- Pool size and behavior configured via connection string options
- Example: `mongodb://host:27017/db?maxPoolSize=50&minPoolSize=10`

## DOs

- Use connection string options for fine-tuning (timeouts, pool sizes, SSL)
- Handle null connection strings gracefully (from `getVisibleUrl()`)
- Set appropriate socket timeouts for network stability
- Use DNS seed list format (`mongodb+srv://`) for MongoDB Atlas
- Close database connections in finally blocks or try-with-resources
- Test connection string parsing with various formats

## DON'Ts

- Don't hardcode connection strings in code (use properties files)
- Don't log raw connection strings (contains credentials) - use `getVisibleUrl()`
- Don't create multiple `MongoClient` instances unnecessarily (expensive)
- Don't forget to call `close()` - leaks connections
- Don't use deprecated connection string options (check MongoDB docs)
- Don't assume single-server deployments (support replica sets, sharded clusters)

## Configuration Options

Common connection string options:

| Option | Purpose | Example |
|--------|---------|---------|
| `authSource` | Authentication database | `?authSource=admin` |
| `replicaSet` | Replica set name | `?replicaSet=rs0` |
| `ssl` | Enable TLS/SSL | `?ssl=true` |
| `maxPoolSize` | Max connection pool size | `?maxPoolSize=50` |
| `socketTimeoutMS` | Socket read timeout | `?socketTimeoutMS=30000` |
| `connectTimeoutMS` | Connection timeout | `?connectTimeoutMS=10000` |
| `serverSelectionTimeoutMS` | Server selection timeout | `?serverSelectionTimeoutMS=30000` |
| `retryWrites` | Retry failed writes | `?retryWrites=true` |

## Transaction Support

**MongoDB 4.0+ Multi-Document Transactions:**
- Supported for replica sets and sharded clusters
- Not supported for standalone MongoDB instances
- Use with caution (performance overhead)
- Automatic transaction management by Liquibase (when enabled)

```java
// Check if transactions are supported
if (database.supports(DatabaseProperty.TRANSACTIONS)) {
    // Execute in transaction context
}
```

## Troubleshooting

**Connection Timeouts:**
- Increase `connectTimeoutMS` and `serverSelectionTimeoutMS`
- Check network connectivity to MongoDB
- Verify MongoDB is running and accepting connections

**Authentication Failures:**
- Ensure `authSource` matches user's authentication database
- Verify username/password are correct
- Check MongoDB user permissions

**SSL/TLS Issues:**
- Use `?ssl=true` for encrypted connections
- Configure trust stores for certificate validation
- Use `mongodb+srv://` for automatic SSL with Atlas

## Related Packages

- `liquibase.ext.mongodb.lockservice` - Uses MongoConnection for locking
- `liquibase.ext.mongodb.changelog` - Uses MongoConnection for history
- `liquibase.ext.mongodb.statement` - Statements use MongoConnection for execution
- `liquibase.nosql.database` - Abstract NoSQL database base
