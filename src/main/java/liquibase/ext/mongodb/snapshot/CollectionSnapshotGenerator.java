package liquibase.ext.mongodb.snapshot;

import com.mongodb.client.MongoDatabase;
import liquibase.database.Database;
import liquibase.exception.DatabaseException;
import liquibase.ext.mongodb.database.MongoLiquibaseDatabase;
import liquibase.ext.mongodb.structure.Collection;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.InvalidExampleException;
import liquibase.snapshot.SnapshotGenerator;
import liquibase.snapshot.SnapshotGeneratorChain;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.Schema;
import org.bson.Document;

import java.util.HashSet;
import java.util.Set;

import static liquibase.plugin.Plugin.PRIORITY_ADDITIONAL;
import static liquibase.plugin.Plugin.PRIORITY_DEFAULT;
import static liquibase.plugin.Plugin.PRIORITY_NONE;

/**
 * Snapshots Mongo collections so that they participate in Liquibase's {@code generateChangelog}/{@code diff}
 * machinery like any other {@link DatabaseObject} type. Mirrors core's TableSnapshotGenerator pattern: it is the
 * default generator for {@link Collection} and additionally attaches itself to {@link Schema} so that snapshotting
 * a schema also enumerates its collections.
 */
public class CollectionSnapshotGenerator implements SnapshotGenerator {

    private static final Set<String> SKIPPED_COLLECTION_NAMES = new HashSet<>();

    static {
        SKIPPED_COLLECTION_NAMES.add("DATABASECHANGELOG");
        SKIPPED_COLLECTION_NAMES.add("DATABASECHANGELOGLOCK");
    }

    private static final String SYSTEM_COLLECTION_PREFIX = "system.";

    @Override
    public int getPriority(Class<? extends DatabaseObject> objectType, Database database) {
        if (!(database instanceof MongoLiquibaseDatabase)) {
            return PRIORITY_NONE;
        }
        if (Collection.class.isAssignableFrom(objectType)) {
            return PRIORITY_DEFAULT;
        }
        if (Schema.class.isAssignableFrom(objectType)) {
            return PRIORITY_ADDITIONAL;
        }
        return PRIORITY_NONE;
    }

    @Override
    public Class<? extends DatabaseObject>[] addsTo() {
        //noinspection unchecked
        return new Class[]{Schema.class};
    }

    @Override
    public Class<? extends SnapshotGenerator>[] replaces() {
        // Core's addsTo={Schema.class} generators consider themselves applicable to any AbstractJdbcDatabase,
        // which Mongo's database class extends, and would otherwise crash trying real JDBC calls under Schema.
        //noinspection unchecked
        return new Class[]{
                liquibase.snapshot.jvm.TableSnapshotGenerator.class,
                liquibase.snapshot.jvm.ViewSnapshotGenerator.class,
                liquibase.snapshot.jvm.SequenceSnapshotGenerator.class
        };
    }

    @Override
    public <T extends DatabaseObject> T snapshot(T example, DatabaseSnapshot snapshot, SnapshotGeneratorChain chain) throws DatabaseException, InvalidExampleException {
        if (example instanceof Collection) {
            //noinspection unchecked
            return (T) snapshotCollection((Collection) example, snapshot);
        }

        final DatabaseObject chainResponse = chain.snapshot(example, snapshot);
        if (chainResponse == null) {
            return null;
        }

        if (example instanceof Schema && snapshot.getSnapshotControl().shouldInclude(Collection.class)) {
            addTo((Schema) chainResponse, snapshot);
        }

        //noinspection unchecked
        return (T) chainResponse;
    }

    private Collection snapshotCollection(Collection example, DatabaseSnapshot snapshot) {
        final MongoDatabase mongoDatabase = ((MongoLiquibaseDatabase) snapshot.getDatabase()).getMongoDatabase();
        for (Document collectionInfo : mongoDatabase.listCollections()) {
            final String collectionName = collectionInfo.getString("name");
            if (!collectionName.equalsIgnoreCase(example.getName()) || shouldSkip(collectionName, collectionInfo)) {
                continue;
            }
            return new Collection(collectionName, example.getSchema())
                    .setOptions(collectionInfo.get("options", Document.class));
        }
        return null;
    }

    private void addTo(Schema schema, DatabaseSnapshot snapshot) {
        final MongoDatabase mongoDatabase = ((MongoLiquibaseDatabase) snapshot.getDatabase()).getMongoDatabase();
        for (Document collectionInfo : mongoDatabase.listCollections()) {
            final String collectionName = collectionInfo.getString("name");
            if (shouldSkip(collectionName, collectionInfo)) {
                continue;
            }
            schema.addDatabaseObject(new Collection(collectionName, schema));
        }
    }

    private boolean shouldSkip(String collectionName, Document collectionInfo) {
        return SKIPPED_COLLECTION_NAMES.contains(collectionName)
                || collectionName.startsWith(SYSTEM_COLLECTION_PREFIX)
                || !"collection".equals(collectionInfo.getString("type"));
    }
}
