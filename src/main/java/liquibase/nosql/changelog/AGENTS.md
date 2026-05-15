# AGENTS.md - NoSQL Abstract Changelog Tracking

## Purpose

This package provides abstract base classes for NoSQL changelog history services, defining the contract for tracking changeset execution across different NoSQL databases. Key responsibilities:

- Define changelog persistence interface
- Abstract changeset record storage and retrieval
- Provide template for history operations
- Enable consistent tracking across NoSQL databases
- Support rollback and versioning operations

This is the foundation layer that MongoDB-specific history services extend.

## Key Files

- `AbstractNoSqlHistoryService.java` - Abstract base class for NoSQL history services
- `NoSqlRanChangeSet.java` - Abstract data model for executed changeset records (if exists)

## Architecture

**History Service Hierarchy:**
```
liquibase.changelog.ChangeLogHistoryService (interface)
└── liquibase.changelog.AbstractChangeLogHistoryService
    └── AbstractNoSqlHistoryService (NoSQL base)
        └── MongoHistoryService (MongoDB implementation)
```

**Key Abstractions:**
- **History Service**: Manages changeset execution records
- **RanChangeSet**: Data model for executed changeset metadata
- **Query Operations**: Retrieve executed changesets
- **Persistence Operations**: Store changeset execution records

## AbstractNoSqlHistoryService

**Purpose:** Base class for all NoSQL history services in Liquibase.

**Key Responsibilities:**
- Define template for history operations
- Abstract storage mechanism (collections vs. tables)
- Provide common validation logic
- Handle changeset lifecycle (init, setExecType, getRanChangeSets)
- Support upgrade scenarios (adjust existing structures)

**Abstract Methods (must be implemented by subclasses):**
```java
protected abstract void init() throws DatabaseException;
protected abstract List<RanChangeSet> queryExecutedChangeSets() throws DatabaseException;
protected abstract void markChangeSetRun(ChangeSet changeSet) throws DatabaseException;
```

**Template Methods (common logic, calls abstract methods):**
```java
public List<RanChangeSet> getRanChangeSets() throws DatabaseException {
    if (!hasDatabaseChangeLogTable()) {
        return Collections.emptyList();
    }
    return queryExecutedChangeSets();  // Subclass implements
}
```

## Testing

- **Run tests for this package**: `mvn test -Dtest=*NoSqlHistoryServiceTest`
- **Test file pattern**: `*NoSqlHistoryServiceTest.java` in `src/test/java/liquibase/nosql/changelog/`
- **Focus**: Abstract behavior and template methods, not database-specific storage

## Dependencies

**Internal:**
- `liquibase.nosql.database.AbstractNoSqlDatabase` - Database abstraction
- `liquibase.changelog.*` - Liquibase core changelog interfaces

**External:**
- `liquibase-core:4.33.0` - Liquibase framework

## Important Patterns

### Template Method Pattern
Abstract class defines algorithm, subclasses implement database-specific steps:

```java
// AbstractNoSqlHistoryService provides template
public void init() throws DatabaseException {
    if (!hasDatabaseChangeLogTable()) {
        // Common validation
        validateDatabase();
        // Subclass creates storage
        createChangeLogStorage();
    } else {
        // Subclass adjusts if needed
        adjustChangeLogStorage();
    }
}

// MongoDB implementation
public class MongoHistoryService extends AbstractNoSqlHistoryService {
    protected void createChangeLogStorage() {
        // Create DATABASECHANGELOG collection
        database.execute(new CreateChangeLogCollectionStatement());
    }
}
```

### Changeset Record Lifecycle
```java
// 1. Check if changeset already ran
List<RanChangeSet> ranChangeSets = getRanChangeSets();
boolean alreadyRan = ranChangeSets.contains(changeSet);

// 2. Execute changeset if not ran
if (!alreadyRan) {
    changeSet.execute(database);
}

// 3. Mark as executed
setExecType(changeSet, ExecType.EXECUTED);

// 4. Store in history
markChangeSetRun(changeSet);  // Subclass implements storage
```

### Storage Abstraction
```java
// Abstract: Doesn't specify storage format
protected abstract void markChangeSetRun(ChangeSet changeSet);

// MongoDB: Stores as BSON document in collection
public void markChangeSetRun(ChangeSet changeSet) {
    Document doc = toDocument(changeSet);
    collection.insertOne(doc);
}

// Couchbase: Could store as JSON document in bucket
public void markChangeSetRun(ChangeSet changeSet) {
    JsonObject json = toJsonObject(changeSet);
    bucket.insert(changeSet.getId(), json);
}
```

## DOs

- Extend `AbstractNoSqlHistoryService` for new NoSQL databases
- Implement all abstract methods completely
- Use database-native storage mechanisms (collections, buckets, tables)
- Ensure thread-safe changeset recording
- Handle upgrades from old storage formats gracefully
- Store all Liquibase metadata fields (checksum, contexts, labels, etc.)
- Test with concurrent changeset executions

## DON'Ts

- Don't put database-specific storage logic in abstract class
- Don't assume specific storage structure (documents vs. records)
- Don't skip checksum validation (prevents duplicate execution)
- Don't modify changeset records after storage (immutable history)
- Don't create deep inheritance hierarchies
- Don't reference specific database APIs in abstract layer

## Changeset Record Fields

All NoSQL history services must track these Liquibase fields:

| Field | Purpose | Type |
|-------|---------|------|
| `id` | Changeset identifier | String |
| `author` | Changeset author | String |
| `fileName` | Changelog file path | String |
| `dateExecuted` | Execution timestamp | Date |
| `orderExecuted` | Sequence number | Integer |
| `execType` | Execution type | Enum (EXECUTED, RERAN, etc.) |
| `md5sum` | Checksum | String |
| `description` | Changeset description | String |
| `comments` | User comments | String |
| `tag` | Release tag | String |
| `liquibaseVersion` | Liquibase version | String |
| `contexts` | Execution contexts | String |
| `labels` | Execution labels | String |
| `deploymentId` | Deployment identifier | String |

## Design Rationale

**Why Separate NoSQL History Service?**

1. **Storage Differences**:
   - SQL: Relational table with fixed schema
   - NoSQL: Collections/buckets with flexible documents

2. **Query Patterns**:
   - SQL: Standard SQL SELECT queries
   - NoSQL: Database-specific query APIs

3. **Migration**:
   - SQL: ALTER TABLE for schema changes
   - NoSQL: Document structure evolution, schema-less

4. **Indexing**:
   - SQL: Standard CREATE INDEX
   - NoSQL: Database-specific index creation

**Comparison to SQL:**
| Aspect | SQL (JdbcHistoryService) | NoSQL (AbstractNoSqlHistoryService) |
|--------|--------------------------|-------------------------------------|
| Storage | DATABASECHANGELOG table | Collection/bucket |
| Query | SQL SELECT | Database-specific API |
| Schema | Fixed columns | Flexible documents |
| Upgrade | ALTER TABLE | Document evolution |
| Connection | JDBC | Database-specific driver |

## Implementation Checklist

When creating a new NoSQL history service:

- [ ] Extend `AbstractNoSqlHistoryService`
- [ ] Implement `init()` - Create storage if not exists
- [ ] Implement `queryExecutedChangeSets()` - Retrieve all records
- [ ] Implement `markChangeSetRun()` - Store new changeset record
- [ ] Implement `hasDatabaseChangeLogTable()` - Check storage exists
- [ ] Implement `getRanChangeSetList()` - Query with filters
- [ ] Create converter: ChangeSet ↔ Native storage format
- [ ] Handle checksum calculation and validation
- [ ] Support rollback queries (by tag, count, date)
- [ ] Test concurrent execution scenarios
- [ ] Test upgrade from old storage formats

## Rollback Support

History service must support rollback queries:

```java
// Get changesets to rollback by tag
List<RanChangeSet> getRanChangeSetsSince(String tag);

// Get last N changesets to rollback
List<RanChangeSet> getRanChangeSets(int count);

// Get changesets executed after date
List<RanChangeSet> getRanChangeSetsSince(Date date);
```

Order: Most recent first (LIFO for rollback)

## Upgrade Scenarios

History service must handle upgrades:

1. **Old Liquibase Version → New Version**:
   - New fields added to changeset records
   - Abstract class provides default values
   - Subclass migrates old records if needed

2. **Storage Format Changes**:
   - Collection/bucket structure evolution
   - Subclass implements upgrade logic in `adjustChangeLogStorage()`

3. **Backward Compatibility**:
   - New code must read old format
   - Gradual migration during normal operations

## Related Packages

- `liquibase.ext.mongodb.changelog` - Concrete MongoDB history service
- `liquibase.nosql.database` - Database abstraction used by history service
- `liquibase.changelog` - Liquibase core changelog interfaces
- `liquibase.ext.mongodb.lockservice` - Lock service (uses similar patterns)
