package liquibase.ext.mongodb.snapshot;

import com.mongodb.client.MongoDatabase;
import liquibase.database.Database;
import liquibase.exception.DatabaseException;
import liquibase.ext.mongodb.database.MongoLiquibaseDatabase;
import liquibase.ext.mongodb.structure.Collection;
import liquibase.ext.mongodb.structure.Index;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.InvalidExampleException;
import liquibase.snapshot.SnapshotGenerator;
import liquibase.snapshot.SnapshotGeneratorChain;
import liquibase.structure.DatabaseObject;
import org.bson.Document;

import static liquibase.plugin.Plugin.PRIORITY_ADDITIONAL;
import static liquibase.plugin.Plugin.PRIORITY_DEFAULT;
import static liquibase.plugin.Plugin.PRIORITY_NONE;

/**
 * Snapshots Mongo indexes, attaching itself to {@link Collection} the same way CollectionSnapshotGenerator
 * attaches itself to Schema, so that snapshotting a collection also enumerates its indexes.
 */
public class IndexSnapshotGenerator implements SnapshotGenerator {

    private static final String ID_INDEX_NAME = "_id_";

    @Override
    public int getPriority(Class<? extends DatabaseObject> objectType, Database database) {
        if (!(database instanceof MongoLiquibaseDatabase)) {
            return PRIORITY_NONE;
        }
        if (Index.class.isAssignableFrom(objectType)) {
            return PRIORITY_DEFAULT;
        }
        if (Collection.class.isAssignableFrom(objectType)) {
            return PRIORITY_ADDITIONAL;
        }
        return PRIORITY_NONE;
    }

    @Override
    public Class<? extends DatabaseObject>[] addsTo() {
        //noinspection unchecked
        return new Class[]{Collection.class};
    }

    @Override
    public Class<? extends SnapshotGenerator>[] replaces() {
        return null;
    }

    @Override
    public <T extends DatabaseObject> T snapshot(T example, DatabaseSnapshot snapshot, SnapshotGeneratorChain chain) throws DatabaseException, InvalidExampleException {
        if (example instanceof Index) {
            //noinspection unchecked
            return (T) snapshotIndex((Index) example, snapshot);
        }

        final DatabaseObject chainResponse = chain.snapshot(example, snapshot);
        if (chainResponse == null) {
            return null;
        }

        if (example instanceof Collection && snapshot.getSnapshotControl().shouldInclude(Index.class)) {
            addTo((Collection) chainResponse, snapshot);
        }

        //noinspection unchecked
        return (T) chainResponse;
    }

    private Index snapshotIndex(Index example, DatabaseSnapshot snapshot) {
        final MongoDatabase mongoDatabase = ((MongoLiquibaseDatabase) snapshot.getDatabase()).getMongoDatabase();
        final Collection collection = example.getCollection();
        for (Document indexInfo : mongoDatabase.getCollection(collection.getName()).listIndexes()) {
            final String indexName = indexInfo.getString("name");
            if (!indexName.equalsIgnoreCase(example.getName()) || ID_INDEX_NAME.equals(indexName)) {
                continue;
            }
            return toIndex(indexInfo, collection);
        }
        return null;
    }

    private void addTo(Collection collection, DatabaseSnapshot snapshot) {
        final MongoDatabase mongoDatabase = ((MongoLiquibaseDatabase) snapshot.getDatabase()).getMongoDatabase();
        for (Document indexInfo : mongoDatabase.getCollection(collection.getName()).listIndexes()) {
            final String indexName = indexInfo.getString("name");
            if (ID_INDEX_NAME.equals(indexName)) {
                continue;
            }
            collection.addDatabaseObject(toIndex(indexInfo, collection));
        }
    }

    private Index toIndex(Document indexInfo, Collection collection) {
        final Index index = new Index(indexInfo.getString("name"), collection)
                .setKeys(indexInfo.get("key", Document.class))
                .setIndexInfo(indexInfo);
        if (Boolean.TRUE.equals(indexInfo.getBoolean("unique"))) {
            index.setUnique(true);
        }
        return index;
    }
}
