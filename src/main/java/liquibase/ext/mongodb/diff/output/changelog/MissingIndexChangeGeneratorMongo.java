package liquibase.ext.mongodb.diff.output.changelog;

import liquibase.change.Change;
import liquibase.database.Database;
import liquibase.diff.output.DiffOutputControl;
import liquibase.diff.output.changelog.AbstractChangeGenerator;
import liquibase.diff.output.changelog.ChangeGeneratorChain;
import liquibase.diff.output.changelog.MissingObjectChangeGenerator;
import liquibase.ext.mongodb.database.MongoLiquibaseDatabase;
import liquibase.ext.mongodb.snapshot.MongoSnapshotAttributes;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.Column;
import liquibase.structure.core.Index;
import org.bson.Document;

public class MissingIndexChangeGeneratorMongo extends AbstractChangeGenerator implements MissingObjectChangeGenerator {

    @Override
    public int getPriority(final Class<? extends DatabaseObject> objectType, final Database database) {
        if ((database instanceof MongoLiquibaseDatabase) && Index.class.isAssignableFrom(objectType)) {
            return PRIORITY_DATABASE;
        }
        return PRIORITY_NONE;
    }

    @Override
    public Class<? extends DatabaseObject>[] runAfterTypes() {
        return new Class[]{liquibase.structure.core.Table.class};
    }

    @Override
    public Class<? extends DatabaseObject>[] runBeforeTypes() {
        return null;
    }

    @Override
    public Change[] fixMissing(final DatabaseObject missingObject,
                               final DiffOutputControl control,
                               final Database referenceDatabase,
                               final Database comparisonDatabase,
                               final ChangeGeneratorChain chain) {
        final Index missingIndex = (Index) missingObject;

        final liquibase.ext.mongodb.change.CreateIndexChange change =
                new liquibase.ext.mongodb.change.CreateIndexChange();
        change.setCollectionName(missingIndex.getRelation().getName());
        change.setKeys(resolveKeys(missingIndex));
        change.setOptions(resolveOptions(missingIndex));
        return new Change[]{change};
    }

    private String resolveKeys(final Index missingIndex) {
        final String keys = missingIndex.getAttribute(MongoSnapshotAttributes.INDEX_KEYS, String.class);
        if (keys != null) {
            return keys;
        }

        final Document fallbackKeys = new Document();
        for (Column column : missingIndex.getColumns()) {
            fallbackKeys.put(column.getName(), Boolean.TRUE.equals(column.getDescending()) ? -1 : 1);
        }
        return fallbackKeys.isEmpty() ? null : fallbackKeys.toJson();
    }

    private String resolveOptions(final Index missingIndex) {
        final String options = missingIndex.getAttribute(MongoSnapshotAttributes.INDEX_OPTIONS, String.class);
        if (options != null) {
            return options;
        }

        final Document fallbackOptions = new Document();
        if (missingIndex.getName() != null) {
            fallbackOptions.put("name", missingIndex.getName());
        }
        if (Boolean.TRUE.equals(missingIndex.isUnique())) {
            fallbackOptions.put("unique", true);
        }
        return fallbackOptions.isEmpty() ? null : fallbackOptions.toJson();
    }
}
