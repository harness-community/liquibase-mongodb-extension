# AGENTS.md - MongoDB Preconditions

## Purpose

This package provides precondition checks for MongoDB changesets. Preconditions allow conditional execution of changesets based on database state:

- Execute changeset only if conditions are met
- Skip changeset if conditions fail (or error/halt)
- Prevent errors from running changesets against incompatible state
- Enable idempotent migrations (run only when needed)
- Support complex conditional logic in changelogs

Preconditions are evaluated before changeset execution.

## Key Files

- `AbstractMongoPrecondition.java` - Base class for all MongoDB preconditions
- `CollectionExistsPrecondition.java` - Checks if collection exists
- `DocumentExistsPrecondition.java` - Checks if document matching filter exists
- `ExpectedDocumentCountPrecondition.java` - Validates document count in collection
- `MongoIndexExistsPrecondition.java` - Checks if index exists on collection (Harness enhancement)

## Architecture

**Precondition Evaluation Flow:**
1. Before executing changeset, Liquibase evaluates preconditions
2. Each precondition's `check()` method queries database
3. If all preconditions pass, changeset executes
4. If any precondition fails, action taken based on `onFail` attribute:
   - `HALT` - Stop execution completely
   - `MARK_RAN` - Skip changeset, mark as ran
   - `WARN` - Log warning, continue execution
   - `CONTINUE` - Skip changeset, continue silently

**Class Hierarchy:**
```
liquibase.precondition.Precondition (interface)
└── liquibase.precondition.AbstractPrecondition
    └── AbstractMongoPrecondition (MongoDB base)
        ├── CollectionExistsPrecondition
        ├── DocumentExistsPrecondition
        ├── ExpectedDocumentCountPrecondition
        └── MongoIndexExistsPrecondition
```

## Precondition Types

### CollectionExistsPrecondition
**Purpose:** Check if MongoDB collection exists.

**Usage:**
```xml
<preConditions onFail="MARK_RAN">
    <ext:collectionExists collectionName="users"/>
</preConditions>
```

**Use Case:** Skip changeset if collection already exists (idempotent migrations).

### DocumentExistsPrecondition
**Purpose:** Check if document matching filter exists in collection.

**Usage:**
```xml
<preConditions onFail="HALT">
    <ext:documentExists collectionName="config">
        <ext:filter>{"key": "app_version"}</ext:filter>
    </ext:documentExists>
</preConditions>
```

**Use Case:** Ensure required configuration document exists before migration.

### ExpectedDocumentCountPrecondition
**Purpose:** Validate document count matches expected value or range.

**Usage:**
```xml
<preConditions onFail="WARN">
    <ext:expectedDocumentCount collectionName="users" expectedCount="0"/>
</preConditions>
```

**Use Case:** Ensure collection is empty before initial data load.

### MongoIndexExistsPrecondition (Harness Enhancement)
**Purpose:** Check if specific index exists on collection.

**Usage:**
```xml
<preConditions onFail="MARK_RAN">
    <ext:mongoIndexExists collectionName="users" indexName="email_1"/>
</preConditions>
```

**Use Case:** Skip index creation if index already exists (safer index management).

## Testing

- **Run tests for this package**: `mvn test -Dtest=*PreconditionTest`
- **Test file pattern**: `*PreconditionTest.java` in `src/test/java/liquibase/ext/mongodb/precondition/`
- **Integration tests**: Precondition tests require MongoDB connection for actual state checks

## Dependencies

**Internal:**
- `liquibase.ext.mongodb.database.MongoConnection` - Database access for checks
- `liquibase.ext.mongodb.statement.*` - Statements for querying database state
- `liquibase.precondition.*` - Liquibase core precondition interfaces

**External:**
- `liquibase-core:4.33.0` - Liquibase framework
- `org.mongodb:mongodb-driver-sync` - MongoDB operations for checks

## Important Patterns

### Precondition Implementation
```java
@Override
public void check(Database database, DatabaseChangeLog changelog, ChangeSet changeset) 
    throws PreconditionFailedException {
    
    MongoConnection connection = (MongoConnection) database.getConnection();
    boolean exists = checkCollectionExists(connection, collectionName);
    
    if (!exists) {
        throw new PreconditionFailedException(
            "Collection '" + collectionName + "' does not exist",
            changelog, this
        );
    }
}
```

### Filter Parsing
```java
// JSON filter string converted to BSON Document
Document filterDoc = Document.parse(filter);
long count = collection.countDocuments(filterDoc);
```

### Error Handling
```java
// Precondition failures throw specific exception
throw new PreconditionFailedException(message, changelog, precondition);

// Database errors propagate as runtime exceptions
catch (MongoException e) {
    throw new PreconditionErrorException(e, changelog, this);
}
```

## Example Changelog with Preconditions

```xml
<databaseChangeLog
    xmlns:ext="http://www.liquibase.org/xml/ns/mongodb"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/mongodb
        http://www.liquibase.org/xml/ns/mongodb/liquibase-mongodb-latest.xsd">

    <!-- Only create collection if it doesn't exist -->
    <changeSet id="1" author="dev">
        <preConditions onFail="MARK_RAN">
            <not>
                <ext:collectionExists collectionName="users"/>
            </not>
        </preConditions>
        <ext:createCollection collectionName="users"/>
    </changeSet>

    <!-- Only create index if it doesn't exist -->
    <changeSet id="2" author="dev">
        <preConditions onFail="MARK_RAN">
            <ext:collectionExists collectionName="users"/>
            <not>
                <ext:mongoIndexExists collectionName="users" indexName="email_1"/>
            </not>
        </preConditions>
        <ext:createIndex collectionName="users">
            <ext:keys>{"email": 1}</ext:keys>
        </ext:createIndex>
    </changeSet>

    <!-- Only seed data if collection is empty -->
    <changeSet id="3" author="dev">
        <preConditions onFail="MARK_RAN">
            <ext:expectedDocumentCount collectionName="users" expectedCount="0"/>
        </preConditions>
        <ext:insertMany collectionName="users">
            <ext:documents>
                [
                    {"email": "admin@example.com", "role": "admin"},
                    {"email": "user@example.com", "role": "user"}
                ]
            </ext:documents>
        </ext:insertMany>
    </changeSet>

    <!-- Ensure config document exists before migration -->
    <changeSet id="4" author="dev">
        <preConditions onFail="HALT">
            <ext:documentExists collectionName="config">
                <ext:filter>{"key": "schema_version"}</ext:filter>
            </ext:documentExists>
        </preConditions>
        <!-- Migration that depends on schema_version config -->
    </changeSet>
</databaseChangeLog>
```

## DOs

- Use preconditions to make changesets idempotent
- Choose appropriate `onFail` action based on criticality:
  - `HALT` - Critical precondition (data consistency requirement)
  - `MARK_RAN` - Idempotent changeset (skip if already applied)
  - `WARN` - Advisory check (log but continue)
- Combine preconditions with `<and>`, `<or>`, `<not>` for complex logic
- Test preconditions with both passing and failing scenarios
- Document why precondition is needed in changeset comments
- Use `MongoIndexExistsPrecondition` before creating indexes (prevents errors)

## DON'Ts

- Don't use preconditions for data validation - that's for constraints
- Don't perform complex queries in preconditions (performance impact)
- Don't modify database state in precondition checks (read-only)
- Don't use `onFail="CONTINUE"` for critical preconditions
- Don't duplicate precondition logic - extract to reusable preconditions
- Don't check preconditions that Liquibase already validates (e.g., duplicate changesetIds)

## Precondition Actions

| Action | Behavior | Use Case |
|--------|----------|----------|
| `HALT` | Stop execution immediately | Critical prerequisite missing |
| `MARK_RAN` | Skip changeset, record as executed | Idempotent operations |
| `WARN` | Log warning, continue execution | Advisory checks |
| `CONTINUE` | Silently skip changeset | Optional optimizations |

## Performance Considerations

- Preconditions add query overhead before each changeset
- Use targeted queries (indexed fields) for better performance
- Avoid `DocumentExistsPrecondition` with complex filters on large collections
- Consider caching precondition results for repeated checks (future enhancement)

## Related Packages

- `liquibase.ext.mongodb.statement` - Statements used by preconditions for checks
- `liquibase.ext.mongodb.database` - Database connection for precondition queries
- `liquibase.precondition` - Liquibase core precondition framework
