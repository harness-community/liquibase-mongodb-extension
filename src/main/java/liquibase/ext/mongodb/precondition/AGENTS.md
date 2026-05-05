# AGENTS.md - MongoDB Preconditions

## Purpose

This package contains **Liquibase precondition implementations** for MongoDB. Preconditions are checks that must pass before a changeset is executed. They enable conditional changeset execution based on database state.

Preconditions are specified in Liquibase changelogs (XML, YAML, JSON) using `<preConditions>` tags.

## Key Files

- **`CollectionExistsPrecondition.java`** - Check if a MongoDB collection exists
  - Validates collection name is present before executing changeset
  - Example: Only create index if collection exists

- **`DocumentExistsPrecondition.java`** - Check if a document matching criteria exists
  - Uses MongoDB query filter to find documents
  - Example: Only insert if document doesn't already exist

- **`ExpectedDocumentCountPrecondition.java`** - Verify document count matches expected value
  - Checks collection has specific number of documents
  - Example: Ensure migration prerequisites are met

- **`MongoIndexExistsPrecondition.java`** - Check if an index exists on a collection
  - **Recently added** (DBOPS-2368) - 2 commits in 6 months
  - Validates index presence before creating or modifying
  - Example: Skip index creation if already exists

## Testing

- **Run tests for this package**: `mvn test -Dtest=*PreconditionTest`
- **Test file pattern**: `*Precondition.java` → `*PreconditionTest.java` in `src/test/java/liquibase/ext/mongodb/precondition/`
- **Integration tests**: Preconditions tested in `MongoLiquibaseIT.java` with real MongoDB
- **Test changelog**: `src/test/resources/liquibase/ext/changelog.index-precondition.test.xml`

## Dependencies

**Internal:**
- `liquibase.ext.mongodb.database.MongoConnection` - MongoDB connection
- `liquibase.ext.mongodb.database.MongoLiquibaseDatabase` - Database implementation

**External:**
- Liquibase Core - `liquibase.precondition.*`
- MongoDB Java Driver - For collection and document queries
- Lombok - For getters/setters

## Important Patterns

### Precondition Registration

Preconditions use `@PreconditionProperty` annotation:
```java
@PreconditionProperty(
    name = "mongoIndexExists",
    description = "Checks if the specified index exists",
    supportsDatabase = MongoLiquibaseDatabase.class
)
```

### Check Method Implementation

All preconditions implement `check()` method from Liquibase's `Precondition` interface:
```java
@Override
public void check(Database database, DatabaseChangeLog changelog, ChangeSet changeset, ChangeExecListener changeExecListener) 
    throws PreconditionFailedException, PreconditionErrorException {
    // Perform check and throw exception if fails
}
```

### Error vs Failure

- **PreconditionFailedException** - Expected condition not met (e.g., collection doesn't exist)
- **PreconditionErrorException** - Unexpected error during check (e.g., connection lost)

### MongoDB Query Usage

Preconditions use MongoDB Java Driver directly:
```java
MongoConnection connection = (MongoConnection) database.getConnection();
MongoDatabase mongoDatabase = connection.getMongoDatabase();
boolean exists = mongoDatabase.listCollectionNames()
    .into(new ArrayList<>())
    .contains(collectionName);
```

## Usage in Changelogs

### XML Example
```xml
<changeSet id="1" author="user">
    <preConditions onFail="MARK_RAN">
        <ext:mongoIndexExists collectionName="users" indexName="email_idx"/>
    </preConditions>
    <ext:dropIndex collectionName="users" indexName="email_idx"/>
</changeSet>
```

### YAML Example
```yaml
- changeSet:
    id: 1
    author: user
    preConditions:
      - onFail: MARK_RAN
      - mongoIndexExists:
          collectionName: users
          indexName: email_idx
    changes:
      - dropIndex:
          collectionName: users
          indexName: email_idx
```

## DOs

- Implement both `check()` methods (with and without ChangeExecListener)
- Use `@PreconditionProperty` annotation with descriptive name and description
- Throw `PreconditionFailedException` for expected failures (condition not met)
- Throw `PreconditionErrorException` for unexpected errors (technical issues)
- Add unit tests for precondition logic
- Add integration tests with real MongoDB
- Provide clear error messages explaining why precondition failed
- Register new preconditions in `META-INF/services/liquibase.precondition.Precondition`
- Update XSD schema files when adding new precondition attributes
- Use Lombok annotations for getters/setters

## DON'Ts

- Don't modify database state in preconditions - they are read-only checks
- Don't catch and swallow exceptions - let them propagate properly
- Don't add complex business logic - keep preconditions simple and fast
- Don't ignore connection errors - throw PreconditionErrorException
- Don't hardcode collection or document values - use parameterized properties
- Don't perform expensive operations (full collection scans) without warning users
- Don't skip validation of required parameters

## Precondition Behaviors (onFail)

Users can configure precondition failure behavior:
- **HALT** (default) - Stop changeset execution and fail
- **MARK_RAN** - Mark changeset as ran without executing
- **WARN** - Log warning and continue execution
- **CONTINUE** - Ignore precondition failure and continue

## Recent Development Focus

**MongoIndexExistsPrecondition** (DBOPS-2368):
- Recently added to support index existence checking
- Enables safer index management (check before create/drop)
- 2 commits in 6 months - new feature
- Complements existing collection and document preconditions

## Common Use Cases

1. **Idempotent changesets**: Check if change already applied
   - `mongoIndexExists` - Skip if index already created
   - `collectionExists` - Skip if collection already exists

2. **Data validation**: Ensure prerequisites are met
   - `documentExists` - Verify reference data is present
   - `expectedDocumentCount` - Confirm migration state

3. **Environment-specific changes**: Apply changes conditionally
   - Combined with `dbms` precondition
   - Combined with custom preconditions

## Testing Strategy

1. **Unit tests**: Mock MongoDB connection and test precondition logic
2. **Integration tests**: Use real MongoDB (Docker) to test actual queries
3. **Failure scenarios**: Test both success and failure paths
4. **Error handling**: Test connection errors and invalid parameters

## Integration with Change Types

Preconditions are independent of change types but commonly used with:
- Index operations (`createIndex`, `dropIndex`)
- Collection operations (`createCollection`, `dropCollection`)
- Document operations (`insertOne`, `insertMany`)
- Mongosh commands (`mongo`, `mongoFile`)
