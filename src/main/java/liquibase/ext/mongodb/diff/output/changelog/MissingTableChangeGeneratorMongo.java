package liquibase.ext.mongodb.diff.output.changelog;

import liquibase.change.Change;
import liquibase.database.Database;
import liquibase.diff.output.DiffOutputControl;
import liquibase.diff.output.changelog.AbstractChangeGenerator;
import liquibase.diff.output.changelog.ChangeGeneratorChain;
import liquibase.diff.output.changelog.MissingObjectChangeGenerator;
import liquibase.ext.mongodb.change.CreateCollectionChange;
import liquibase.ext.mongodb.database.MongoLiquibaseDatabase;
import liquibase.ext.mongodb.snapshot.MongoSnapshotAttributes;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.Index;
import liquibase.structure.core.Table;

public class MissingTableChangeGeneratorMongo extends AbstractChangeGenerator implements MissingObjectChangeGenerator {

    @Override
    public int getPriority(final Class<? extends DatabaseObject> objectType, final Database database) {
        if ((database instanceof MongoLiquibaseDatabase) && Table.class.isAssignableFrom(objectType)) {
            return PRIORITY_DATABASE;
        }
        return PRIORITY_NONE;
    }

    @Override
    public Class<? extends DatabaseObject>[] runAfterTypes() {
        return null;
    }

    @Override
    public Class<? extends DatabaseObject>[] runBeforeTypes() {
        return new Class[]{Index.class};
    }

    @Override
    public Change[] fixMissing(final DatabaseObject missingObject,
                               final DiffOutputControl control,
                               final Database referenceDatabase,
                               final Database comparisonDatabase,
                               final ChangeGeneratorChain chain) {
        final Table missingTable = (Table) missingObject;

        final CreateCollectionChange change = new CreateCollectionChange();
        change.setCollectionName(missingTable.getName());
        change.setOptions(missingTable.getAttribute(MongoSnapshotAttributes.COLLECTION_OPTIONS, String.class));
        return new Change[]{change};
    }
}
