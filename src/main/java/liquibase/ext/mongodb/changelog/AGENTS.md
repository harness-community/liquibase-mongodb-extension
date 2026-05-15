# AGENTS.md - MongoDB Changelog Tracking

## Purpose

This package implements changelog history tracking for MongoDB, maintaining a record of all executed changesets in the `DATABASECHANGELOG` collection. This enables:

- Tracking which changesets have been executed
- Preventing duplicate execution of changesets
- Supporting rollback operations
- Auditing database changes over time
- Calculating checksums to detect changeset modifications

The history service is the persistence layer for Liquibase's change tracking mechanism.

## Key Files

- `MongoHistoryService.java` - Main history service implementation for MongoDB
- `MongoRanChangeSet.java` - Data model for executed changeset records
- `MongoRanChangeSetToDocumentConverter.java` - Bidirectional converter between changeset objects and BSON documents
- `CreateChangeLogCollectionStatement.java` - Creates the changelog collection on first run
- `AdjustChangeLogCollectionStatement.java` - Adjusts existing changelog collection structure
- `GetMaxChangeSetSequenceStatement.java` - Retrieves the maximum order sequence number

## Architecture

**Changeset Tracking Flow:**
1. Before executing changeset, Liquibase queries history service via `getRanChangeSets()`
2. History service reads from `DATABASECHANGELOG` collection
3. If changeset not found, it's executed
4. After successful execution, `setExecType()` and changeset is inserted/updated
5. Checksum is calculated and stored to detect future modifications

**Changeset Record Structure:**
```
MongoRanChangeSet
├── id - Changeset identifier
├── author - Changeset author
├── fileName - Changelog file path
├── dateExecuted - Execution timestamp
├── orderExecuted - Sequence number
├── execType - Type of execution (EXECUTED, RERAN, SKIPPED, etc.)
├── md5sum - Checksum of changeset content
├── description - Changeset description
├── comments - User comments
├── tag - Release tag
├── liquibaseVersion - Version that executed changeset
├── contexts - Execution contexts
├── labels - Execution labels
└── deploymentId - Deployment identifier
```

## Testing

- **Run tests for this package**: `mvn test -Dtest=*HistoryServiceTest`
- **Test file pattern**: `*HistoryServiceTest.java` in `src/test/java/liquibase/ext/mongodb/changelog/`
- **Integration tests**: History service tests require MongoDB to verify changeset persistence

## Dependencies

**Internal:**
- `liquibase.nosql.changelog.AbstractNoSqlHistoryService` - Base NoSQL history service
- `liquibase.ext.mongodb.database.MongoConnection` - MongoDB connection
- `liquibase.ext.mongodb.statement.*` - Statements for changelog operations

**External:**
- `org.mongodb:mongodb-driver-sync` - For document operations
- `liquibase.changelog.*` - Liquibase core changelog interfaces

## Important Patterns

### Changeset Persistence
```java
// Convert RanChangeSet to BSON Document
Document doc = MongoRanChangeSetToDocumentConverter.toDocument(ranChangeSet);

// Insert into DATABASECHANGELOG collection
collection.insertOne(doc);
```

### Duplicate Prevention
- Composite key: `{id, author, fileName}` ensures uniqueness
- History service checks for existing changeset before allowing execution
- Checksum comparison detects modified changesets

### Sequence Ordering
- `orderExecuted` field maintains execution order
- `GetMaxChangeSetSequenceStatement` finds highest sequence for next changeset
- Critical for rollback operations (LIFO order)

### Document Conversion
- Bidirectional converter handles all Liquibase metadata fields
- ISO date format for timestamps
- Null-safe conversion for optional fields

## DOs

- Always verify `DATABASECHANGELOG` collection exists before querying
- Use consistent changelog file paths (relative paths recommended)
- Set meaningful contexts and labels for filtering changesets
- Include descriptive comments for complex changesets
- Regularly backup `DATABASECHANGELOG` collection (critical audit data)
- Use tags to mark release boundaries

## DON'Ts

- Don't manually modify `DATABASECHANGELOG` collection (breaks checksums)
- Don't delete changeset records (breaks change history)
- Don't change changeset IDs after execution (creates duplicate tracking)
- Don't modify executed changesets (checksum validation will fail)
- Don't use absolute file paths in changesets (not portable)

## Changelog Collection Structure

```javascript
// DATABASECHANGELOG collection
{
    "_id": ObjectId("..."),
    "fileName": "db/changelog/v1.0.xml",
    "id": "1",
    "author": "developer",
    "dateExecuted": ISODate("2024-05-14T10:30:00Z"),
    "orderExecuted": 1,
    "execType": "EXECUTED",
    "md5sum": "8:d41d8cd98f00b204e9800998ecf8427e",
    "description": "createCollection collectionName=users",
    "comments": "Initial users collection",
    "tag": null,
    "liquibaseVersion": "4.33.0",
    "contexts": null,
    "labels": null,
    "deploymentId": "4823456789"
}
```

## Checksum Validation

**Purpose:** Detect modified changesets after execution

- Checksums are MD5 hashes of changeset content (format: `version:hash`)
- On re-run, Liquibase recalculates checksum and compares to stored value
- Mismatch triggers `ValidationFailedException` by default
- Use `validCheckSum` attribute to whitelist expected checksum changes

**Common Causes of Checksum Failures:**
- Editing SQL/commands in executed changeset
- Whitespace changes in changeset content
- Attribute order changes in XML
- Version upgrade changing checksum calculation algorithm

## Rollback Support

The history service supports rollback by:
1. Querying changesets in reverse `orderExecuted` order
2. Filtering by tag, count, or date
3. Executing rollback statements defined in changesets
4. Updating `execType` to `ROLLED_BACK`

**Note:** Not all changes support automatic rollback - some require manual rollback SQL.

## Troubleshooting

**Checksum Failures:**
```bash
# Allow checksum validation to pass for modified changeset
<changeSet id="1" author="dev">
    <validCheckSum>8:oldhash</validCheckSum>
    <!-- modified content -->
</changeSet>
```

**Clear Checksums (for development only):**
```bash
liquibase clearCheckSums
```

**View Change History:**
```bash
# Query MongoDB directly
db.DATABASECHANGELOG.find().sort({orderExecuted: -1})

# Or use Liquibase
liquibase history
```

## Related Packages

- `liquibase.ext.mongodb.lockservice` - Lock service (uses similar patterns)
- `liquibase.ext.mongodb.database` - MongoDB connection
- `liquibase.nosql.changelog` - Abstract NoSQL history service base
