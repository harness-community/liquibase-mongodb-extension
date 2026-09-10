package liquibase.ext.mongodb.diff;

import liquibase.change.Change;
import liquibase.database.Database;
import liquibase.diff.output.DiffOutputControl;
import liquibase.diff.output.changelog.AbstractChangeGenerator;
import liquibase.diff.output.changelog.ChangeGeneratorChain;
import liquibase.diff.output.changelog.MissingObjectChangeGenerator;
import liquibase.ext.mongodb.change.CreateIndexChange;
import liquibase.ext.mongodb.structure.Index;
import liquibase.structure.DatabaseObject;
import org.bson.Document;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MissingIndexChangeGenerator extends AbstractChangeGenerator implements MissingObjectChangeGenerator {

    private static final Set<String> INDEX_METADATA_EXCLUDED_KEYS = new HashSet<>(Arrays.asList("key", "v", "ns", "name"));

    @Override
    public int getPriority(Class<? extends DatabaseObject> objectType, Database database) {
        if (Index.class.isAssignableFrom(objectType)) {
            return PRIORITY_DEFAULT;
        }
        return PRIORITY_NONE;
    }

    @Override
    public Class<? extends DatabaseObject>[] runAfterTypes() {
        //noinspection unchecked
        return new Class[]{liquibase.ext.mongodb.structure.Collection.class};
    }

    @Override
    public Class<? extends DatabaseObject>[] runBeforeTypes() {
        return null;
    }

    @Override
    public Change[] fixMissing(DatabaseObject missingObject, DiffOutputControl control, Database referenceDatabase, Database comparisonDatabase, ChangeGeneratorChain chain) {
        final Index missingIndex = (Index) missingObject;
        final Document indexInfo = missingIndex.getIndexInfo();
        final Document keys = missingIndex.getKeys();

        final Document options = new Document();
        if (indexInfo != null) {
            for (Map.Entry<String, Object> entry : indexInfo.entrySet()) {
                if (!INDEX_METADATA_EXCLUDED_KEYS.contains(entry.getKey())) {
                    options.put(entry.getKey(), entry.getValue());
                }
            }
        }
        options.put("name", missingIndex.getName());

        final CreateIndexChange change = new CreateIndexChange();
        change.setCollectionName(missingIndex.getCollection().getName());
        change.setKeys((keys == null ? new Document() : keys).toJson());
        change.setOptions(options.toJson());

        return new Change[]{change};
    }
}
