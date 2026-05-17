package liquibase.ext.mongodb.snapshot;

import liquibase.exception.DatabaseException;
import liquibase.ext.mongodb.database.MongoLiquibaseDatabase;
import liquibase.nosql.snapshot.NoSqlSnapshotGenerator;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.InvalidExampleException;
import liquibase.snapshot.SnapshotGenerator;
import liquibase.snapshot.SnapshotGeneratorChain;
import liquibase.structure.DatabaseObject;

public abstract class AbstractMongoSnapshotGenerator implements SnapshotGenerator {

    private static final int MONGO_PRIORITY = PRIORITY_DATABASE + 10;

    private final Class<? extends DatabaseObject> defaultFor;
    private final Class<? extends DatabaseObject>[] addsTo;

    protected AbstractMongoSnapshotGenerator(final Class<? extends DatabaseObject> defaultFor) {
        this(defaultFor, null);
    }

    protected AbstractMongoSnapshotGenerator(final Class<? extends DatabaseObject> defaultFor,
                                             final Class<? extends DatabaseObject>[] addsTo) {
        this.defaultFor = defaultFor;
        this.addsTo = addsTo;
    }

    @Override
    public int getPriority(final Class<? extends DatabaseObject> objectType, final liquibase.database.Database database) {
        if (!(database instanceof MongoLiquibaseDatabase) || objectType == null) {
            return PRIORITY_NONE;
        }

        if ((defaultFor != null) && defaultFor.isAssignableFrom(objectType)) {
            return MONGO_PRIORITY;
        }

        if (addsTo != null) {
            for (Class<? extends DatabaseObject> type : addsTo) {
                if (type.isAssignableFrom(objectType)) {
                    return PRIORITY_ADDITIONAL;
                }
            }
        }

        return PRIORITY_NONE;
    }

    @Override
    public Class<? extends DatabaseObject>[] addsTo() {
        return addsTo;
    }

    @Override
    public <T extends DatabaseObject> T snapshot(final T example,
                                                 final DatabaseSnapshot snapshot,
                                                 final SnapshotGeneratorChain chain)
            throws DatabaseException, InvalidExampleException {
        if ((defaultFor != null) && (example != null) && defaultFor.isAssignableFrom(example.getClass())) {
            return (T) snapshotObject(example, snapshot);
        }

        final DatabaseObject chainResponse = chain.snapshot(example, snapshot);
        if (chainResponse == null) {
            return null;
        }

        if ((example != null) && shouldAddTo(example.getClass(), snapshot) && (addsTo != null)) {
            for (Class<? extends DatabaseObject> addType : addsTo) {
                if (addType.isAssignableFrom(example.getClass())) {
                    addTo(chainResponse, snapshot);
                }
            }
        }

        return (T) chainResponse;
    }

    protected boolean shouldAddTo(final Class<? extends DatabaseObject> databaseObjectType,
                                  final DatabaseSnapshot snapshot) {
        return (defaultFor != null) && snapshot.getSnapshotControl().shouldInclude(defaultFor);
    }

    @Override
    public Class<? extends SnapshotGenerator>[] replaces() {
        return new Class[]{NoSqlSnapshotGenerator.class};
    }

    protected MongoLiquibaseDatabase getMongoLiquibaseDatabase(final DatabaseSnapshot snapshot) {
        return (MongoLiquibaseDatabase) snapshot.getDatabase();
    }

    protected abstract DatabaseObject snapshotObject(DatabaseObject example, DatabaseSnapshot snapshot)
            throws DatabaseException, InvalidExampleException;

    protected abstract void addTo(DatabaseObject foundObject, DatabaseSnapshot snapshot)
            throws DatabaseException, InvalidExampleException;
}
