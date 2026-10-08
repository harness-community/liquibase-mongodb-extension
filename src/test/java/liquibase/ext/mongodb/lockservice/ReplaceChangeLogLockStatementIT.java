package liquibase.ext.mongodb.lockservice;

import liquibase.ext.AbstractMongoIntegrationTest;
import liquibase.lockservice.ChangeLogLockOwner;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class ReplaceChangeLogLockStatementIT extends AbstractMongoIntegrationTest {

    private static final String LOCK_COLLECTION_NAME = "lockCollection";
    private static final String HARNESS_OWNED_PREVIOUS =
            "HI|pfx|plan-old|dead-host (10.0.0.1)";
    private static final String HARNESS_OWNED_OTHER =
            "HI|pfx|plan-other|other-host (10.0.0.2)";

    private final MongoChangeLogLockToDocumentConverter converter = new MongoChangeLogLockToDocumentConverter();

    @BeforeEach
    void requireLockedByPrefix() {
        System.setProperty(ChangeLogLockOwner.ENV_HARNESS_LOCKEDBY_PREFIX, "pfx");
    }

    @AfterEach
    void clearLockedByPrefix() {
        System.clearProperty(ChangeLogLockOwner.ENV_HARNESS_LOCKEDBY_PREFIX);
        System.clearProperty("liquibase.hostDescription");
    }

    @Test
    void lock() {
        final ReplaceChangeLogLockStatement lockStatement = new ReplaceChangeLogLockStatement(LOCK_COLLECTION_NAME, true);

        assertThat(lockStatement.update(database)).isOne();
    }

    @Test
    void multipleLock() {
        final ReplaceChangeLogLockStatement lockStatement = new ReplaceChangeLogLockStatement(LOCK_COLLECTION_NAME, true);

        lockStatement.update(database);

        assertThat(lockStatement.update(database)).isZero();
    }

    @Test
    void unlock() {
        final ReplaceChangeLogLockStatement unlockStatement = new ReplaceChangeLogLockStatement(LOCK_COLLECTION_NAME, false);

        assertThat(unlockStatement.update(database)).isOne();
    }

    @Test
    void lockThenUnlock() {
        final ReplaceChangeLogLockStatement lockStatement = new ReplaceChangeLogLockStatement(LOCK_COLLECTION_NAME, true);
        final ReplaceChangeLogLockStatement unlockStatement = new ReplaceChangeLogLockStatement(LOCK_COLLECTION_NAME, false);

        lockStatement.update(database);

        assertThat(unlockStatement.update(database)).isOne();
    }

    @Test
    void lockThenUnlockByDifferentHost() {
        final ReplaceChangeLogLockStatement lockStatement = new ReplaceChangeLogLockStatement(LOCK_COLLECTION_NAME, true);
        final ReplaceChangeLogLockStatement unlockStatement = new ReplaceChangeLogLockStatement(LOCK_COLLECTION_NAME, false);

        System.setProperty("liquibase.hostDescription", "lockHost");
        lockStatement.update(database);
        System.setProperty("liquibase.hostDescription", "unlockHost");

        assertThat(unlockStatement.update(database)).isZero();
    }

    @Test
    void forceUnlockByDifferentHost() {
        final ReplaceChangeLogLockStatement lockStatement = new ReplaceChangeLogLockStatement(LOCK_COLLECTION_NAME, true);
        final ReplaceChangeLogLockStatement forceUnlock =
                new ReplaceChangeLogLockStatement(LOCK_COLLECTION_NAME, false, null, true);

        System.setProperty("liquibase.hostDescription", "lockHost");
        lockStatement.update(database);
        System.setProperty("liquibase.hostDescription", "unlockHost");

        assertThat(forceUnlock.update(database)).isOne();
    }

    @Test
    void unlockThenLock() {
        final ReplaceChangeLogLockStatement unlockStatement = new ReplaceChangeLogLockStatement(LOCK_COLLECTION_NAME, false);
        final ReplaceChangeLogLockStatement lockStatement = new ReplaceChangeLogLockStatement(LOCK_COLLECTION_NAME, true);

        unlockStatement.update(database);

        assertThat(lockStatement.update(database)).isOne();
    }

    @Test
    void unlockThenLockByDifferentHost() {
        final ReplaceChangeLogLockStatement unlockStatement = new ReplaceChangeLogLockStatement(LOCK_COLLECTION_NAME, false);
        final ReplaceChangeLogLockStatement lockStatement = new ReplaceChangeLogLockStatement(LOCK_COLLECTION_NAME, true);

        System.setProperty("liquibase.hostDescription", "unlockHost");
        unlockStatement.update(database);
        System.setProperty("liquibase.hostDescription", "lockHost");

        assertThat(lockStatement.update(database)).isOne();
    }

    @Test
    void stealWithExactPreviousOwner_returnsOneAndTakesOwnership() {
        seedLockedRow(HARNESS_OWNED_PREVIOUS);

        final ReplaceChangeLogLockStatement steal =
                new ReplaceChangeLogLockStatement(LOCK_COLLECTION_NAME, true, HARNESS_OWNED_PREVIOUS);

        assertThat(steal.update(database)).isOne();

        Document row = connection.getMongoDatabase().getCollection(LOCK_COLLECTION_NAME).find().first();
        assertThat(row).isNotNull();
        assertThat(row.getBoolean(MongoChangeLogLock.Fields.locked)).isTrue();
        assertThat(row.getString(MongoChangeLogLock.Fields.lockedBy)).isEqualTo(MongoChangeLogLock.formLockedBy());
    }

    @Test
    void freeAcquireWhileLocked_returnsZero() {
        seedLockedRow(HARNESS_OWNED_PREVIOUS);

        final ReplaceChangeLogLockStatement freeAcquire =
                new ReplaceChangeLogLockStatement(LOCK_COLLECTION_NAME, true);

        assertThat(freeAcquire.update(database)).isZero();

        Document row = connection.getMongoDatabase().getCollection(LOCK_COLLECTION_NAME).find().first();
        assertThat(row).isNotNull();
        assertThat(row.getString(MongoChangeLogLock.Fields.lockedBy)).isEqualTo(HARNESS_OWNED_PREVIOUS);
    }

    @Test
    void stealWithDifferentPreviousOwner_returnsZero() {
        seedLockedRow(HARNESS_OWNED_PREVIOUS);

        final ReplaceChangeLogLockStatement stealWrongOwner =
                new ReplaceChangeLogLockStatement(LOCK_COLLECTION_NAME, true, HARNESS_OWNED_OTHER);

        assertThat(stealWrongOwner.update(database)).isZero();

        Document row = connection.getMongoDatabase().getCollection(LOCK_COLLECTION_NAME).find().first();
        assertThat(row).isNotNull();
        assertThat(row.getBoolean(MongoChangeLogLock.Fields.locked)).isTrue();
        assertThat(row.getString(MongoChangeLogLock.Fields.lockedBy)).isEqualTo(HARNESS_OWNED_PREVIOUS);
    }

    private void seedLockedRow(String lockedBy) {
        Document doc = converter.toDocument(new MongoChangeLogLock(1, new Date(), lockedBy, true));
        connection.getMongoDatabase().getCollection(LOCK_COLLECTION_NAME).insertOne(doc);
    }
}
