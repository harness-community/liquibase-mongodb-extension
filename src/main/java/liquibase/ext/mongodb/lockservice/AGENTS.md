# AGENTS.md - MongoDB Lock Service

## Purpose

This package implements distributed locking for MongoDB to prevent concurrent Liquibase executions from conflicting. The lock service ensures that only one Liquibase instance can modify the database schema at a time, critical for:

- Multi-instance deployments (microservices, Kubernetes pods)
- CI/CD pipelines running in parallel
- Development environments with multiple developers

The lock is stored in the `DATABASECHANGELOGLOCK` collection and uses MongoDB's atomic operations for thread-safe locking.

## Key Files

- `MongoLockService.java` - Main lock service implementation for MongoDB
- `MongoChangeLogLock.java` - Lock data model (lock ID, locked flag, locked by, lock date)
- `MongoChangeLogLockToDocumentConverter.java` - Bidirectional converter between lock objects and BSON documents
- `CreateChangeLogLockCollectionStatement.java` - Creates the lock collection on first run
- `AdjustChangeLogLockCollectionStatement.java` - Adjusts existing lock collection structure
- `SelectChangeLogLockStatement.java` - Queries current lock state
- `ReplaceChangeLogLockStatement.java` - Atomically acquires/releases locks using replaceOne

## Architecture

**Lock Mechanism:**
1. Liquibase attempts to acquire lock on startup
2. `MongoLockService.acquireLock()` uses `ReplaceChangeLogLockStatement`
3. MongoDB `replaceOne()` with `upsert:true` provides atomic lock acquisition
4. Lock document stores: `{ _id: 1, locked: true, lockedBy: "hostname", lockGranted: ISODate }`
5. Lock is released on successful completion or error
6. Stale locks can be manually released via `releaseLock()`

**Class Responsibilities:**
```
MongoLockService
├── init() - Creates DATABASECHANGELOGLOCK collection if needed
├── acquireLock() - Atomically acquires lock (blocking with timeout)
├── releaseLock() - Releases current lock
├── listLocks() - Returns all locks (typically one)
└── forceReleaseLock() - Forces lock release (use with caution)
```

## Testing

- **Run tests for this package**: `mvn test -Dtest=*LockServiceTest`
- **Test file pattern**: `*LockServiceTest.java` in `src/test/java/liquibase/ext/mongodb/lockservice/`
- **Integration tests**: Lock service tests require MongoDB connection to verify atomic locking

## Dependencies

**Internal:**
- `liquibase.nosql.lockservice.AbstractNoSqlLockService` - Base NoSQL lock service
- `liquibase.ext.mongodb.database.MongoConnection` - MongoDB connection for operations
- `liquibase.ext.mongodb.statement.*` - Statements for lock operations

**External:**
- `org.mongodb:mongodb-driver-sync` - For atomic `replaceOne()` operations
- `liquibase.lockservice.*` - Liquibase core lock service interfaces

## Important Patterns

### Atomic Lock Acquisition
```java
// Uses MongoDB's replaceOne with upsert for atomic compare-and-swap
ReplaceChangeLogLockStatement statement = new ReplaceChangeLogLockStatement(
    hasChangeLogLock() ? existingLock : newLock, 
    locked
);
```

### Lock Timeout Handling
- Default timeout: 5 minutes (configurable via `liquibase.lockWaitTime`)
- Service polls lock status with exponential backoff
- Throws `LockException` if timeout exceeded

### Document Conversion
- Bidirectional converter ensures consistent serialization
- BSON document structure mirrors Liquibase's SQL lock table
- ISO date format for `lockGranted` timestamp

## DOs

- Always call `releaseLock()` in finally blocks
- Use `forceReleaseLock()` only for manual intervention (stale locks)
- Set reasonable `lockWaitTime` for your deployment pattern
- Monitor `DATABASECHANGELOGLOCK` collection for stale locks
- Test lock behavior with multiple Liquibase instances in parallel

## DON'Ts

- Don't manually modify `DATABASECHANGELOGLOCK` collection during execution
- Don't disable locking in production environments
- Don't set extremely long lock timeouts (causes slow failure detection)
- Don't assume locks are automatically cleaned up after crashes (may need manual release)
- Don't delete the lock collection - it will be recreated but may cause race conditions

## Lock Collection Structure

```javascript
// DATABASECHANGELOGLOCK collection
{
    "_id": 1,                          // Lock ID (always 1 for single lock)
    "locked": true,                    // Lock state
    "lockedBy": "hostname-12345",      // Host/process that acquired lock
    "lockGranted": ISODate("2024-...") // When lock was acquired
}
```

## Troubleshooting

**Stale Lock Issues:**
- If Liquibase crashes or is killed, lock may remain acquired
- Manually release: `db.DATABASECHANGELOGLOCK.updateOne({_id:1}, {$set:{locked:false}})`
- Or use Liquibase CLI: `liquibase releaseLocks`

**Lock Timeout:**
- Increase `liquibase.lockWaitTime` in `liquibase.properties`
- Check for long-running changesets blocking lock release
- Verify MongoDB connection is stable (network issues can cause timeouts)

## Related Packages

- `liquibase.ext.mongodb.changelog` - History service (uses similar document patterns)
- `liquibase.ext.mongodb.database` - MongoDB connection for lock operations
- `liquibase.nosql.lockservice` - Abstract NoSQL lock service base
